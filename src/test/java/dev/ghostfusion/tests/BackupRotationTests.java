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
        for(String badKey : new String[]{"-1","65536","65","not-an-integer"}) {
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
    /** R2.97: closing an unchanged ClickGUI/host never rewrites healthy
     * primary/backup files, while the backup is bootstrapped and damage heals. */
    private static void idempotentSaves() throws Exception {
        final java.nio.file.attribute.FileTime marker=
            java.nio.file.attribute.FileTime.fromMillis(946684800000L);
        Path profileDir=Files.createTempDirectory("ghostc-idempotent-profile");
        ModuleRegistry registry=new ModuleRegistry();
        registry.register(new Fixture(),44,new Setting.Bool("safe",true));
        ProfileStore profile=new ProfileStore(profileDir,registry);
        Path primary=profileDir.resolve("default.properties");
        Path backup=profileDir.resolve("default.properties.bak");
        profile.save("default");
        ok(!Files.exists(backup),"first save remains primary-only");
        profile.save("default");
        ok(Files.exists(backup),"second same-value save bootstraps backup");
        byte[] prior=bytes(primary),priorBackup=bytes(backup);
        Files.setLastModifiedTime(primary,marker);
        Files.setLastModifiedTime(backup,marker);
        for(int i=0;i<512;i++) {
            profile.save("default");
            ok(Arrays.equals(prior,bytes(primary)) &&
               Arrays.equals(priorBackup,bytes(backup)),
               "unchanged profile preserves exact file bytes "+i);
            ok(Files.getLastModifiedTime(primary).equals(marker) &&
               Files.getLastModifiedTime(backup).equals(marker),
               "unchanged profile preserves write timestamps "+i);
        }
        registry.bind("test.feature",43);
        profile.save("default");
        ok("43".equals(read(primary).getProperty("module.test.feature.key")) &&
           Arrays.equals(prior,bytes(backup)),
           "changed profile writes once and rotates its verified previous value");
        // The first save of a changed value keeps the previous-good backup;
        // its next save must checkpoint the new value for crash recovery.
        profile.save("default");
        ok(Arrays.equals(bytes(primary),bytes(backup)),
           "second changed-profile save checkpoints latest state for recovery");
        Properties damagedBackup=read(backup);
        damagedBackup.setProperty("module.test.feature.key","malformed");
        write(backup,damagedBackup); // checksum mismatch
        byte[] newer=bytes(primary);
        profile.save("default");
        ok(Arrays.equals(newer,bytes(backup)) &&
           ConfigIntegrity.verify(ConfigIntegrity.read(backup)),
           "unchanged profile repairs corrupted backup instead of skipping");
        tamper(primary,"module.test.feature.key","missing-integrity");
        byte[] protectedBackup=bytes(backup);
        profile.save("default");
        ok(ConfigIntegrity.verify(ConfigIntegrity.read(primary)) &&
           Arrays.equals(protectedBackup,bytes(backup)),
           "damaged primary repaired without poisoning last good backup");
        registry.shutdown();
        noTempLeaks(profileDir);

        Path uiDir=Files.createTempDirectory("ghostc-idempotent-ui");
        UiState state=new UiState();
        UiSettingsStore ui=new UiSettingsStore(uiDir,state);
        Path uiPrimary=uiDir.resolve("ui.properties");
        Path uiBackup=uiDir.resolve("ui.properties.bak");
        state.position(.22,.33);
        ui.save();
        ok(!Files.exists(uiBackup),"first UI save has no prior revision");
        ui.save();
        ok(Files.exists(uiBackup),"second UI save bootstraps recovery backup");
        byte[] uiOld=bytes(uiPrimary),uiOldBackup=bytes(uiBackup);
        Files.setLastModifiedTime(uiPrimary,marker);
        Files.setLastModifiedTime(uiBackup,marker);
        for(int i=0;i<512;i++) {
            ui.save();
            ok(Arrays.equals(uiOld,bytes(uiPrimary)) &&
               Arrays.equals(uiOldBackup,bytes(uiBackup)),
               "unchanged UI preserves both files "+i);
            ok(Files.getLastModifiedTime(uiPrimary).equals(marker) &&
               Files.getLastModifiedTime(uiBackup).equals(marker),
               "unchanged UI avoids timestamp changes "+i);
        }
        state.position(.78,.84);
        ui.save();
        ok("0.78".equals(read(uiPrimary).getProperty("hud.x")) &&
           Arrays.equals(uiOld,bytes(uiBackup)),
           "changed HUD anchor writes and backs up previous sealed settings");
        ui.save();
        ok(Arrays.equals(bytes(uiPrimary),bytes(uiBackup)),
           "second changed-UI save checkpoints latest HUD state for recovery");
        Properties uiDamage=read(uiBackup);
        uiDamage.setProperty("theme","NOT_A_THEME");
        ConfigIntegrity.seal(uiDamage); // valid SHA-256 but typed-invalid
        write(uiBackup,uiDamage);
        byte[] uiLatest=bytes(uiPrimary);
        ui.save();
        ok(Arrays.equals(uiLatest,bytes(uiBackup)) &&
           ConfigIntegrity.verify(ConfigIntegrity.read(uiBackup)),
           "unchanged UI repairs typed-invalid sealed backup");
        tamper(uiPrimary,"hud.y","9.0");
        byte[] protectedUiBackup=bytes(uiBackup);
        ui.save();
        ok(ConfigIntegrity.verify(ConfigIntegrity.read(uiPrimary)) &&
           Arrays.equals(protectedUiBackup,bytes(uiBackup)),
           "UI primary damage repaired without rotating over good backup");
        noTempLeaks(uiDir);
    }
    /** R3.06: all recovery staging copies are bounded by the same 1 MiB
     * config budget, and oversize sources cannot create huge .tmp/.corrupt. */
    private static void boundedRecoveryCopies() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-r306-bounded-copies");
        UiState state=new UiState();
        UiSettingsStore store=new UiSettingsStore(dir,state);
        state.position(.42,.24);
        store.save();
        store.save();
        Path primary=dir.resolve("ui.properties");
        Path backup=dir.resolve("ui.properties.bak");
        byte[] safeBackup=Files.readAllBytes(backup);
        byte[] huge=new byte[3*1024*1024+127];
        Arrays.fill(huge,(byte)49);
        Files.write(primary,huge);
        ok(!ConfigIntegrity.rotateVerifiedBackup(primary),
           "oversized primary cannot rotate into valid .bak");
        ok(Arrays.equals(safeBackup,Files.readAllBytes(backup)),
           "oversized failed rotation keeps exact trusted backup");
        noRecoveryTempLeaks(dir);
        ok(store.loadRecovering(),
           "oversized damaged primary recovers from small verified backup");
        ok(ConfigIntegrity.verify(ConfigIntegrity.read(primary)) &&
           Arrays.equals(safeBackup,Files.readAllBytes(backup)),
           "bounded restore promotes only the exact small verified backup");
        ok(!Files.exists(dir.resolve("ui.properties.corrupt")),
           "oversized corrupt primary is never duplicated as huge evidence");
        noRecoveryTempLeaks(dir);

        // The backup itself may be huge: never first copy all of its
        // contents into the private staging directory.
        Files.write(primary,huge);
        byte[] malformedPrimary=Files.readAllBytes(primary);
        Files.write(backup,huge);
        boolean denied=false;
        try {store.loadRecovering();}
        catch(java.io.IOException expected){denied=true;}
        ok(denied,"oversized backup is refused before promotion");
        ok(Arrays.equals(malformedPrimary,Files.readAllBytes(primary)),
           "oversized backup refusal leaves original corrupt primary unchanged");
        ok(Files.size(backup)==huge.length &&
           !Files.exists(dir.resolve("ui.properties.corrupt")),
           "rejected oversized backup creates no false recovery artifact");
        noRecoveryTempLeaks(dir);
        // Also test the direct helper path without a typed load call.
        denied=false;
        try {ConfigIntegrity.restoreBackup(primary);}
        catch(java.io.IOException expected){denied=true;}
        ok(denied && Files.size(primary)==huge.length,
           "direct recovery rejects oversized backup before altering primary");
        noRecoveryTempLeaks(dir);
    }
    /** R3.10: local recovery must never chase a symlink into another
     * directory, including a symlink to an otherwise perfectly valid backup.
     * Confirm no external bytes, primary or trusted backup are replaced. */
    private static void symlinkRecoveryFences() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-r310-links-");
        Path outside=Files.createTempDirectory("ghostc-r310-external-");
        UiState state=new UiState();
        UiSettingsStore store=new UiSettingsStore(dir,state);
        state.position(.22,.44);
        store.save();store.save();
        Path primary=dir.resolve("ui.properties");
        Path backup=dir.resolve("ui.properties.bak");
        byte[] trusted=Files.readAllBytes(backup);
        Path external=outside.resolve("external.properties");
        Files.write(external,trusted);
        byte[] externalBytes=Files.readAllBytes(external);
        byte[] damaged="corrupt-original-without-checksum".getBytes(
            java.nio.charset.StandardCharsets.UTF_8);
        Files.write(primary,damaged);

        // A verified digest does not grant permission to import a backup
        // through an external filesystem alias.
        Files.delete(backup);
        Files.createSymbolicLink(backup,external.toAbsolutePath());
        boolean refused=false;
        try {ConfigIntegrity.restoreBackup(primary);}
        catch(java.io.IOException expected){refused=true;}
        ok(refused && Files.isSymbolicLink(backup),
           "backup alias is rejected before recovery staging");
        ok(Arrays.equals(damaged,Files.readAllBytes(primary)) &&
           Arrays.equals(externalBytes,Files.readAllBytes(external)),
           "backup link refusal preserves local and external bytes");
        noRecoveryTempLeaks(dir);

        // Restore a normal verified backup, then reject a linked primary.
        Files.delete(backup);
        Files.write(backup,trusted);
        Files.delete(primary);
        Files.write(external,damaged);
        Files.createSymbolicLink(primary,external.toAbsolutePath());
        refused=false;
        try {ConfigIntegrity.restoreBackup(primary);}
        catch(java.io.IOException expected){refused=true;}
        ok(refused && Files.isSymbolicLink(primary),
           "primary alias is rejected before creating .corrupt evidence");
        ok(Arrays.equals(damaged,Files.readAllBytes(external)) &&
           Arrays.equals(trusted,Files.readAllBytes(backup)) &&
           !Files.exists(dir.resolve("ui.properties.corrupt")),
           "linked-primary refusal preserves trusted backup and external file");
        ok(!ConfigIntegrity.rotateVerifiedBackup(primary) &&
           Arrays.equals(trusted,Files.readAllBytes(backup)),
           "rotation also rejects linked primary without damaging backup");
        noRecoveryTempLeaks(dir);
    }
    /** R3.11: sealed files outside config may not be loaded through symlinks. */
    private static void symlinkDirectLoadFences() throws Exception {
        Path local=Files.createTempDirectory("ghostc-r311-direct-");
        Path outside=Files.createTempDirectory("ghostc-r311-external-");
        UiState author=new UiState();author.position(.34,.56);
        new UiSettingsStore(local,author).save();
        Path primary=local.resolve("ui.properties");
        Path foreign=outside.resolve("valid-ui.properties");
        Files.move(primary,foreign);
        byte[] external=Files.readAllBytes(foreign);
        Files.createSymbolicLink(primary,foreign.toAbsolutePath());
        UiState live=new UiState();live.position(.77,.22);
        UiSettingsStore ui=new UiSettingsStore(local,live);
        boolean refused=false;
        try{ConfigIntegrity.read(primary);}catch(java.io.IOException expected){refused=true;}
        ok(refused,"direct parser rejects linked but sealed UI file");
        refused=false;
        try{ui.load();}catch(java.io.IOException expected){refused=true;}
        ok(refused && live.hudX()==.77 && live.hudY()==.22,
            "normal UI loader refuses alias without changing live HUD");
        refused=false;
        try{ui.loadRecovering();}catch(java.io.IOException expected){refused=true;}
        ok(refused && live.hudX()==.77 && live.hudY()==.22,
            "UI recovery cannot accept a linked primary either");
        ok(Files.isSymbolicLink(primary) &&
            Arrays.equals(external,Files.readAllBytes(foreign)) &&
            !Files.exists(local.resolve("ui.properties.corrupt")),
            "external UI bytes, alias and evidence unchanged");
        noRecoveryTempLeaks(local);

        ModuleRegistry authorRegistry=new ModuleRegistry();
        authorRegistry.register(new Fixture(),44);
        new ProfileStore(local,authorRegistry).save("default");
        Path profile=local.resolve("default.properties");
        Path outsideProfile=outside.resolve("valid-profile.properties");
        Files.move(profile,outsideProfile);
        byte[] original=Files.readAllBytes(outsideProfile);
        Files.createSymbolicLink(profile,outsideProfile.toAbsolutePath());
        ModuleRegistry liveRegistry=new ModuleRegistry();
        liveRegistry.register(new Fixture(),55);
        ProfileStore loader=new ProfileStore(local,liveRegistry);
        refused=false;
        try{loader.load("default");}catch(java.io.IOException expected){refused=true;}
        ok(refused && liveRegistry.get("test.feature").keybind()==55,
            "normal profile load never imports linked external keybind");
        refused=false;
        try{loader.loadRecovering("default");}
        catch(java.io.IOException expected){refused=true;}
        ok(refused && liveRegistry.get("test.feature").keybind()==55,
            "profile recovery cannot bypass direct admission fence");
        ok(Files.isSymbolicLink(profile) &&
            Arrays.equals(original,Files.readAllBytes(outsideProfile)) &&
            !Files.exists(local.resolve("default.properties.corrupt")),
            "external profile and local alias remain intact");
        noRecoveryTempLeaks(local);
        authorRegistry.shutdown();liveRegistry.shutdown();
    }
    private static void noRecoveryTempLeaks(Path dir) throws Exception {
        try(Stream<Path> files=Files.list(dir)) {
            ok(!files.anyMatch(p->{
                String name=p.getFileName().toString();
                return name.startsWith("ghostc-recover-") ||
                       name.startsWith("ghostc-corrupt-") ||
                       name.startsWith("ghostc-backup-");
            }),"bounded staging deletes all temporary copies");
        }
    }

    public static void main(String[] args) throws Exception {
        profile();ui();idempotentSaves();boundedRecoveryCopies();
        symlinkRecoveryFences();symlinkDirectLoadFences();
        ok(checks>800,"nontrivial backup protection regression matrix");
        System.out.println("BACKUP_ROTATION_PASS assertions="+checks);
    }
}
