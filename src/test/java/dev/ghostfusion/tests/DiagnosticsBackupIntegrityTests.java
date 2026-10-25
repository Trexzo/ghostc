package dev.ghostfusion.tests;

import dev.ghostfusion.core.ConfigIntegrity;
import dev.ghostfusion.host.RuntimeDiagnostics;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

/** R3.26: never rotate malformed, oversized, or linked diagnostic primaries. */
public final class DiagnosticsBackupIntegrityTests {
    private static int checks;
    private static void ok(boolean pass,String message) {
        checks++;if(!pass)throw new AssertionError(message);
    }
    private static void clean(Path directory) throws Exception {
        try(DirectoryStream<Path> files=Files.newDirectoryStream(
                directory,"ghostc-diag-backup-*.tmp")) {
            for(Path file:files)throw new AssertionError("abandoned staged report "+file);
        }
    }
    private static RuntimeDiagnostics initialized(Path directory) throws Exception {
        RuntimeDiagnostics d=new RuntimeDiagnostics();
        d.record(RuntimeDiagnostics.Counter.CLIENT_TICK);
        d.save(directory);
        d.save(directory);
        ok(Files.isRegularFile(directory.resolve("diagnostics-last.properties.bak"),
           LinkOption.NOFOLLOW_LINKS),"baseline has verified report backup");
        return d;
    }
    private static void corruptPrimaryCannotReplaceGoodBackup() throws Exception {
        for(int i=0;i<128;i++) {
            Path folder=Files.createTempDirectory("ghostc-r326-corrupt");
            RuntimeDiagnostics d=initialized(folder);
            Path primary=folder.resolve("diagnostics-last.properties");
            Path backup=folder.resolve("diagnostics-last.properties.bak");
            byte[] baseline=Files.readAllBytes(backup);
            String source=new String(Files.readAllBytes(primary),StandardCharsets.ISO_8859_1);
            String corrupted;
            switch(i%8) {
            case 0: corrupted="schema=999\nscope=counter-only-no-identity\n";break;
            case 1: corrupted=source+"\nprivate.account=SECRET_TOKEN\n";break;
            case 2: corrupted=source.replace(
                "count.client_tick=1","count.client_tick=-17");break;
            case 3: corrupted=source+"\ncount.client_tick=9999\n";break;
            case 4: corrupted=source.replace(
                "scope=counter-only-no-identity","scope=network-and-identities");break;
            case 5: corrupted=source+"\ncount.client_tick=18446744073709551615\n";break;
            case 6: corrupted="schema=1\nscope=counter-only-no-identity\n";break;
            default:
                StringBuilder huge=new StringBuilder("schema=1\n");
                for(int j=0;j<70000;j++)huge.append('x');
                corrupted=huge.toString();
            }
            Files.write(primary,corrupted.getBytes(StandardCharsets.ISO_8859_1));
            d.record(RuntimeDiagnostics.Counter.EXTRA_ATTACK);
            d.save(folder);
            ok(Arrays.equals(baseline,Files.readAllBytes(backup)),
               "invalid primary cannot destroy last known-good diagnostics "+i);
            Properties saved=ConfigIntegrity.read(primary);
            ok("counter-only-no-identity".equals(saved.getProperty("scope")) &&
               "1".equals(saved.getProperty("count.extra_attack")) &&
               !saved.containsKey("private.account"),
               "new report publishes only safe counters "+i);
            clean(folder);
            d.save(folder);
            ok(ConfigIntegrity.read(primary).equals(ConfigIntegrity.read(backup)) &&
               "1".equals(ConfigIntegrity.read(backup).getProperty("count.extra_attack")),
               "healthy report values checkpoint again despite timestamp comments "+i);
        }
    }
    private static void symlinkCannotLeakUnrelatedContents() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-r326-symlink");
        Path external=Files.createTempFile("ghostc-r326-sensitive",".txt");
        byte[] secret="user.email=private@example.invalid\napi.token=SECRET\n"
            .getBytes(StandardCharsets.UTF_8);
        Files.write(external,secret);
        RuntimeDiagnostics d=initialized(folder);
        Path primary=folder.resolve("diagnostics-last.properties");
        Path backup=folder.resolve("diagnostics-last.properties.bak");
        byte[] before=Files.readAllBytes(backup);
        Files.delete(primary);
        try {
            Files.createSymbolicLink(primary,external.toAbsolutePath());
        } catch(UnsupportedOperationException | java.nio.file.FileSystemException unsupported) {
            // Some test hosts cannot create symlinks without elevated rights.
            return;
        }
        ok(Files.isSymbolicLink(primary),"symlink attack fixture created");
        d.save(folder);
        ok(!Files.isSymbolicLink(primary) &&
           Arrays.equals(secret,Files.readAllBytes(external)),
           "saving replaces alias, never mutates linked external file");
        ok(Arrays.equals(before,Files.readAllBytes(backup)) &&
           !new String(Files.readAllBytes(backup),StandardCharsets.ISO_8859_1)
             .contains("SECRET"),
           "external secret is never imported into last good backup");
        clean(folder);
    }
    private static void validTypedReportsStillRotate() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-r326-valid");
        RuntimeDiagnostics d=initialized(folder);
        Path primary=folder.resolve("diagnostics-last.properties");
        Path backup=folder.resolve("diagnostics-last.properties.bak");
        Properties first=ConfigIntegrity.read(primary);
        ok("1".equals(first.getProperty("count.client_tick")),
           "original diagnostic report readable");
        d.record(RuntimeDiagnostics.Counter.CLIENT_TICK);
        d.save(folder);
        Properties previous=ConfigIntegrity.read(backup);
        Properties next=ConfigIntegrity.read(primary);
        ok("1".equals(previous.getProperty("count.client_tick")) &&
           "2".equals(next.getProperty("count.client_tick")),
           "valid old report remains recoverable after newer report publishes");
        d.save(folder);
        ok("2".equals(ConfigIntegrity.read(backup).getProperty("count.client_tick")),
           "second save checkpoints latest valid report");
        clean(folder);
    }
    public static void main(String[] args) throws Exception {
        corruptPrimaryCannotReplaceGoodBackup();
        symlinkCannotLeakUnrelatedContents();
        validTypedReportsStillRotate();
        ok(checks>=500,"substantial diagnostics backup robustness checks");
        System.out.println("DIAGNOSTICS_BACKUP_INTEGRITY_PASS checks="+checks);
    }
}
