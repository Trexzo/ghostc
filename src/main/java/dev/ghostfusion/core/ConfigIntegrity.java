package dev.ghostfusion.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;

/**
 * Local accidental corruption detection, not an authenticity signature.
 * Legacy schema-1 properties without a digest stay readable. Newly saved
 * profiles and UI settings include a canonical SHA-256 over all data fields.
 */
public final class ConfigIntegrity {
    public static final String FIELD = "integrity.sha256";
    private static final long MAX_BYTES=1048576L;
    private static final int MAX_FIELDS=4096;
    private ConfigIntegrity() { }

    public static void seal(Properties p) {
        if(p==null)throw new IllegalArgumentException("properties");
        p.remove(FIELD);
        p.setProperty(FIELD,digest(p));
    }
    /** True for integrity-checked modern files; false for legacy schema=1. */
    public static boolean verify(Properties p) {
        if(p==null)throw new IllegalArgumentException("properties");
        String recorded=p.getProperty(FIELD);
        if(recorded==null) return false;
        if(!recorded.matches("[0-9a-f]{64}") || !recorded.equals(digest(p)))
            throw new IllegalArgumentException("Config integrity mismatch");
        return true;
    }
    private static void length(MessageDigest digest,int value) {
        digest.update((byte)(value>>>24));digest.update((byte)(value>>>16));
        digest.update((byte)(value>>>8));digest.update((byte)value);
    }
    private static void text(MessageDigest digest,String value) {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        length(digest,bytes.length);
        digest.update(bytes);
    }
    private static String digest(Properties p) {
        if(p.size()>MAX_FIELDS)throw new IllegalArgumentException("Too many config fields");
        ArrayList<String> keys=new ArrayList<String>(p.stringPropertyNames());
        Collections.sort(keys);
        MessageDigest sha;
        try { sha=MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException("SHA256 unavailable",ex);}
        for(String key:keys) {
            if(FIELD.equals(key))continue;
            text(sha,key);text(sha,p.getProperty(key));
        }
        StringBuilder out=new StringBuilder(64);
        for(byte b:sha.digest()) {
            int v=b&255;
            out.append(Character.forDigit(v>>>4,16));
            out.append(Character.forDigit(v&15,16));
        }
        return out.toString();
    }
    /** Properties.load otherwise uses last-write-wins for duplicate names,
     * even when two different spellings decode to the same key. Such files
     * have ambiguous authority and must not become verified or recovered
     * config snapshots. Preserve normal Java Properties parsing/escaping. */
    private static final class StrictProperties extends Properties {
        @Override public synchronized Object put(Object key,Object value) {
            if(containsKey(key))
                throw new IllegalArgumentException("Duplicate config key: "+key);
            if(size()>=MAX_FIELDS)
                throw new IllegalArgumentException("Too many config fields");
            return super.put(key,value);
        }
    }
    public static Properties read(Path file) throws IOException {
        if(file==null)throw new IllegalArgumentException("file");
        if(Files.size(file)>MAX_BYTES)throw new IOException("Config size limit exceeded");
        // Bound the actual bytes read, not only a pre-read Files.size().
        // A file can grow while its input stream is open.
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=Files.newInputStream(file)) {
            byte[] block=new byte[8192];
            int count;
            while((count=in.read(block))!=-1) {
                if(count>MAX_BYTES-bytes.size())
                    throw new IOException("Config size limit exceeded");
                bytes.write(block,0,count);
            }
        }
        Properties p=new StrictProperties();
        try(InputStream snapshot=new ByteArrayInputStream(bytes.toByteArray())) {
            p.load(snapshot);
        }
        // Also apply field limit to legacy files with no modern integrity seal.
        if(p.size()>MAX_FIELDS)throw new IllegalArgumentException("Too many config fields");
        verify(p);
        return p;
    }
    /**
     * A close-time save with no logical changes should not rewrite either
     * verified file. Require BOTH a matching sealed primary and a matching,
     * checksum-verified, typed-valid recovery backup before skipping a write.
     * A damaged primary or backup must go through the ordinary safe rotation
     * and atomic save path instead. Legacy unsealed primaries are migrated.
     *
     * A backup of the previous state is useful immediately after a mutation,
     * but the next close must checkpoint the latest state to the backup
     * before later unchanged closes may become no-ops.
     */
    public static boolean unchangedWithRecoveryBackup(Path primary,Properties desired,
            java.util.function.Predicate<Properties> typedValidator) {
        if(primary==null || desired==null || typedValidator==null)
            throw new IllegalArgumentException("primary/desired/validator");
        if(!verify(desired))throw new IllegalArgumentException("unsealed desired config");
        Path backup=primary.resolveSibling(primary.getFileName()+".bak");
        if(!Files.isRegularFile(primary,java.nio.file.LinkOption.NOFOLLOW_LINKS) ||
           !Files.isRegularFile(backup,java.nio.file.LinkOption.NOFOLLOW_LINKS))
            return false;
        try {
            Properties onDisk=read(primary);
            if(!verify(onDisk) || !onDisk.equals(desired) ||
               !typedValidator.test(onDisk))return false;
            Properties recovery=read(backup);
            // A distinct older backup must be refreshed on the next save
            // so recovery restores the latest acknowledged HUD/profile state.
            return verify(recovery) && typedValidator.test(recovery) &&
                recovery.equals(desired);
        } catch(IOException | RuntimeException invalid) {
            return false; // The ordinary save path repairs the valid primary.
        }
    }

    /**
     * Rotate a backup only from the exact bytes of a verified modern config.
     *
     * The source may have become damaged since startup; never overwrite the
     * last known-good .bak with it. Copy to a private temporary file and
     * validate THAT copy before atomically promoting it to the backup.
     * Legacy unsealed files are readable but cannot be authenticated as a
     * safe replacement for an existing verified backup.
     *
     * @return true if a verified primary was promoted; false if no suitable
     *         source exists or its current bytes do not pass validation.
     */
    public static boolean rotateVerifiedBackup(Path primary) throws IOException {
        return rotateVerifiedBackup(primary, new java.util.function.Predicate<Properties>() {
            public boolean test(Properties candidate) { return true; }
        });
    }
    /** Validate the staged, integrity-checked bytes against the caller's load schema. */
    public static boolean rotateVerifiedBackup(Path primary,
            java.util.function.Predicate<Properties> typedValidator) throws IOException {
        if(primary==null || typedValidator==null)
            throw new IllegalArgumentException("primary/validator");
        if(!Files.isRegularFile(primary,java.nio.file.LinkOption.NOFOLLOW_LINKS) ||
            Files.size(primary)>MAX_BYTES) return false;
        Path staged=Files.createTempFile(primary.getParent(),"ghostc-backup-", ".tmp");
        try {
            try {
                Files.copy(primary,staged,StandardCopyOption.REPLACE_EXISTING);
                Properties p=read(staged);
                if(!"1".equals(p.getProperty("schema")) || !verify(p) || !typedValidator.test(p))
                    return false;
            } catch(IOException | RuntimeException corrupted) {
                // A malformed/unsealed primary must not replace the good .bak.
                return false;
            }
            Path backup=primary.resolveSibling(primary.getFileName()+".bak");
            try {
                Files.move(staged,backup,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch(AtomicMoveNotSupportedException ex) {
                Files.move(staged,backup,StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    /**
     * Promote only a checksum-verified and typed-valid backup snapshot.
     * Preserve the corrupt original as .corrupt, without rotating .bak.
     * Legacy unsealed schema-1 files remain readable by ordinary load()
     * but are not trustworthy recovery candidates.
     */
    public static void restoreBackup(Path primary) throws IOException {
        restoreBackup(primary, new java.util.function.Predicate<Properties>() {
            public boolean test(Properties candidate) { return true; }
        });
    }
    /**
     * Recover from one copied, bounded and typed-validated backup snapshot.
     * The caller applies the new runtime state only AFTER this succeeds.
     * Never promote a different backup revision than the one inspected.
     */
    public static void restoreBackup(Path primary,
            java.util.function.Predicate<Properties> typedValidator) throws IOException {
        if(primary==null || typedValidator==null)
            throw new IllegalArgumentException("primary/validator");
        Path backup=primary.resolveSibling(primary.getFileName()+".bak");
        Path temp=Files.createTempFile(primary.getParent(),"ghostc-recover-", ".tmp");
        try {
            Files.copy(backup,temp,StandardCopyOption.REPLACE_EXISTING);
            Properties staged=read(temp);
            // Ordinary read permits legacy schema-1 without a digest.
            // Recovery must be stricter: never promote an unsealed backup.
            if(!"1".equals(staged.getProperty("schema")) || !verify(staged) ||
               !typedValidator.test(staged))
                throw new IllegalArgumentException("Invalid recovered configuration");
            // Keep the original bytes available for incident diagnosis.
            if(Files.exists(primary))Files.copy(primary,
                primary.resolveSibling(primary.getFileName()+".corrupt"),
                StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp,primary,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch(AtomicMoveNotSupportedException ex) {
                Files.move(temp,primary,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
}
