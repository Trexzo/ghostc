package dev.ghostfusion.tests;

import dev.ghostfusion.core.ConfigIntegrity;
import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.ProfileStore;
import dev.ghostfusion.core.Setting;
import dev.ghostfusion.ui.UiSettingsStore;
import dev.ghostfusion.ui.UiState;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Random;
import java.util.stream.Stream;

/** R2.4: a damaged primary may never poison the last verified backup on save. */
public final class BackupRotationTests {
    private static int checks;
    private static void ok(boolean value,String label) {
        checks++;
        if(!value)throw new AssertionError(label);
    }
    private static class Fixture implements GhostModule {
        public String id(){return "test.feature";}
        public String displayName(){return "Test";}
        public Category category(){return Category.UTILITY;}
        public void onEnable(){}
        public void onDisable(){}
        public void onEvent(GameEvent ignored){}
    }
    private static Properties read(Path file) throws Exception {
        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(file)){p.load(in);}
        return p;
    }
    private static void write(Path file,Properties data) throws Exception {
        try(OutputStream out=Files.newOutputStream(file)){data.store(out,"negative input");}
    }
    private static byte[] bytes(Path file) throws Exception {
        return Files.readAllBytes(file);
    }
    private static void tamper(Path primary,String field,String value) throws Exception {
        Properties p=read(primary);
        p.setProperty(field,value);
        // Leave the existing seal intact so a tampered but plausible file fails.
        write(primary,p);
    }
    private static void noTempLeaks(Path dir) throws Exception {
        try(Stream<Path> paths=Files.list(dir)) {
            ok(!paths.anyMatch(x->x.getFileName().toString().startsWith("ghostc-backup-")),
                "all staged verification files removed");
        }
    }
    private static void profile() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-safe-rotation-profile");
        ModuleRegistry registry=new ModuleRegistry();
        Setting.Bool enabledSetting=new Setting.Bool("safe",true);
        registry.register(new Fixture(),44,enabledSetting);
        ProfileStore store=new ProfileStore(dir,registry);
        Path primary=dir.resolve("default.properties");
        Path backup=dir.resolve("default.properties.bak");
        store.save("default");
        ok(!Files.exists(backup),"fresh profile has no previous revision");
        store.save("default");
        ok(ConfigIntegrity.verify(read(backup)),"healthy primary rotates to backup");
        byte[] protectedBackup=bytes(backup);
        tamper(primary,"module.test.feature.key","65");
        ok(!ConfigIntegrity.rotateVerifiedBackup(primary),"direct rotation rejects bad checksum");
        ok(Arrays.equals(protectedBackup,bytes(backup)),"direct refusal preserves old backup");
        for(int i=0;i<225;i++) {
            // Saving after unexpected file damage must not overwrite the only
            // recoverable profile, even when the in-memory settings are valid.
            store.save("default");
            ok(Arrays.equals(protectedBackup,bytes(backup)),
                "save after bad profile preserves last good backup "+i);
            ok(ConfigIntegrity.verify(read(primary)),
                "new profile is sealed and readable "+i);
            if(i<224)tamper(primary,"module.test.feature.key",Integer.toString(65+i));
        }
        noTempLeaks(dir);
        store.save("default");
        ok(ConfigIntegrity.verify(read(backup)),"next healthy save rotates again");
        // A syntactically valid legacy file has no integrity evidence and must
        // not replace the last healthy backup when saved over.
        byte[] latestBackup=bytes(backup);
        Properties legacy=read(primary);
        legacy.remove(ConfigIntegrity.FIELD);
        write(primary,legacy);
        store.save("default");
        ok(Arrays.equals(latestBackup,bytes(backup)),"legacy cannot poison verified backup");
        ok(ConfigIntegrity.verify(read(primary)),"saved legacy upgraded to sealed format");
        // A correctly sealed but unloadable module profile is not a backup.
        byte[] typedSafeBackup=bytes(backup);
        for(String badKey : new String[]{"-1","65536","87","not-an-integer"}) {
            Properties unusable=read(primary);
            unusable.setProperty("module.test.feature.key",badKey);
            ConfigIntegrity.seal(unusable);
            write(primary,unusable);
            ok(ConfigIntegrity.verify(read(primary)),"sealed invalid module fixture");
            store.save("default");
            ok(Arrays.equals(typedSafeBackup,bytes(backup)),
                "unloadable module profile cannot poison backup: "+badKey);
            ok(ConfigIntegrity.verify(read(primary)),"healthy module primary replaces damaged");
        }
        registry.shutdown();
    }
    private static void ui() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-safe-rotation-ui");
        UiState state=new UiState();
        UiSettingsStore store=new UiSettingsStore(dir,state);
        Path primary=dir.resolve("ui.properties"),backup=dir.resolve("ui.properties.bak");
        state.position(.2,.3);store.save();
        state.position(.7,.8);store.save();
        ok(ConfigIntegrity.verify(read(backup)),"healthy UI backup verified");
        byte[] lastGood=bytes(backup);
        Random seeded=new Random(240019L);
        for(int i=0;i<135;i++) {
            // Rotate through multiple damaged fields; none may displace the
            // sealed backup, whether invalid syntax or valid altered values.
            String field=i%3==0?"hud.x":i%3==1?"hud.target.y":"layout";
            String value=i%3==0?"NaN":i%3==1?Double.toString(seeded.nextDouble()):"INVALID";
            tamper(primary,field,value);
            state.position(seeded.nextDouble(),seeded.nextDouble());
            store.save();
            ok(Arrays.equals(lastGood,bytes(backup)),
               "UI backup preserved across damaged-primary save "+i);
            ok(ConfigIntegrity.verify(read(primary)),"new UI primary sealed "+i);
            ok(read(primary).getProperty("hud.x")!=null,"UI retained layout "+i);
        }
        noTempLeaks(dir);
        store.save();
        ok(ConfigIntegrity.verify(read(backup)),"healthy UI source eventually rotates");
        // Refuse damaged/unsealed backup candidates even with direct API calls.
        Properties bad=read(primary);
        bad.setProperty(ConfigIntegrity.FIELD,"not-a-sha256");
        write(primary,bad);
        byte[] prior=bytes(backup);
        ok(!ConfigIntegrity.rotateVerifiedBackup(primary),"malformed digest rejected");
        ok(Arrays.equals(prior,bytes(backup)),"malformed digest never poisons backup");
        Files.write(primary,new byte[1024*1024+1]);
        ok(!ConfigIntegrity.rotateVerifiedBackup(primary),"oversized primary rejected");
        ok(Arrays.equals(prior,bytes(backup)),"oversized primary cannot poison backup");
        store.save();
        ok(Arrays.equals(prior,bytes(backup)),"normal save also rejects oversize backup");
        ok(ConfigIntegrity.verify(read(primary)),"UI recovers to sealed primary");
        noTempLeaks(dir);
        byte[] typedSafeBackup=bytes(backup);
        String[] fields={"layout","theme","hud.x","hud.target.y"};
        String[] values={"NOT_A_LAYOUT","NOT_A_THEME","NaN","1.5"};
        for(int i=0;i<fields.length;i++) {
            Properties unusable=read(primary);
            unusable.setProperty(fields[i],values[i]);
            ConfigIntegrity.seal(unusable);
            write(primary,unusable);
            ok(ConfigIntegrity.verify(read(primary)),"sealed invalid UI fixture");
            store.save();
            ok(Arrays.equals(typedSafeBackup,bytes(backup)),
                "unloadable UI cannot poison backup: "+fields[i]);
            ok(ConfigIntegrity.verify(read(primary)),"healthy UI replaces damaged");
        }
        noTempLeaks(dir);
    }
    public static void main(String[] args) throws Exception {
        profile();ui();
        ok(checks>800,"nontrivial backup protection regression matrix");
        System.out.println("BACKUP_ROTATION_PASS assertions="+checks);
    }
}
