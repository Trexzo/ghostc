package dev.ghostfusion.core;

import java.io.IOException;
import java.io.InputStream;
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
    public static Properties read(Path file) throws IOException {
        if(Files.size(file)>MAX_BYTES)throw new IOException("Config size limit exceeded");
        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(file)) {p.load(in);}
        verify(p);
        return p;
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
        if(primary==null)throw new IllegalArgumentException("primary");
        if(!Files.isRegularFile(primary,java.nio.file.LinkOption.NOFOLLOW_LINKS) ||
            Files.size(primary)>MAX_BYTES) return false;
        Path staged=Files.createTempFile(primary.getParent(),"ghostc-backup-", ".tmp");
        try {
            try {
                Files.copy(primary,staged,StandardCopyOption.REPLACE_EXISTING);
                Properties p=read(staged);
                if(!"1".equals(p.getProperty("schema")) || !verify(p))
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
     * Only called after the backup has been parsed/validated and applied.
     * Preserve the corrupt original as .corrupt, without rotating .bak.
     */
    public static void restoreBackup(Path primary) throws IOException {
        Path backup=primary.resolveSibling(primary.getFileName()+".bak");
        Path temp=Files.createTempFile(primary.getParent(),"ghostc-recover-", ".tmp");
        try {
            Files.copy(backup,temp,StandardCopyOption.REPLACE_EXISTING);
            // Revalidate the staged copy to reject a backup changed in transit.
            read(temp);
            if(Files.exists(primary))Files.copy(primary,
                primary.resolveSibling(primary.getFileName()+".corrupt"),
                StandardCopyOption.REPLACE_EXISTING);
            try {Files.move(temp,primary,StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ex) {
                Files.move(temp,primary,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {Files.deleteIfExists(temp);}
    }
}
