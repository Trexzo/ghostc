package dev.ghostfusion.tests;

import dev.ghostfusion.core.ConfigIntegrity;
import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.ProfileStore;
import dev.ghostfusion.core.Setting;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.ui.UiSettingsStore;
import dev.ghostfusion.ui.HudPositions;
import dev.ghostfusion.ui.UiState;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Random;

/** R2.3: corruption detection, verified fallback and legacy schema-1 migration. */
public final class ConfigRecoveryTests {
    private static int count;
    private static void ok(boolean yes,String why) {
        count++;
        if(!yes)throw new AssertionError(why);
    }
    private static void rejects(Runnable action,String why) {
        boolean threw=false;
        try{action.run();}catch(RuntimeException expected){threw=true;}
        ok(threw,why);
    }
    private static final class Dummy implements GhostModule {
        final String id;
        Dummy(String id){this.id=id;}
        public String id(){return id;}
        public String displayName(){return id;}
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
    private static void write(Path file,Properties p) throws Exception {
        try(OutputStream out=Files.newOutputStream(file)){p.store(out,"fixture");}
    }
    private static void checksum() throws Exception {
        Properties properties=new Properties();
        properties.setProperty("schema","1");
        properties.setProperty("module.example.name","okay");
        ok(!ConfigIntegrity.verify(properties),"legacy checksum optional");
        ConfigIntegrity.seal(properties);
        String correct=properties.getProperty(ConfigIntegrity.FIELD);
        ok(correct!=null&&correct.length()==64,"SHA256 present");
        ok(ConfigIntegrity.verify(properties),"modern properties verified");
        ConfigIntegrity.seal(properties);
        ok(correct.equals(properties.getProperty(ConfigIntegrity.FIELD)),
           "repeated seal deterministic");
        Properties reordered=new Properties();
        reordered.setProperty("module.example.name","okay");
        reordered.setProperty("schema","1");
        ConfigIntegrity.seal(reordered);
        ok(correct.equals(reordered.getProperty(ConfigIntegrity.FIELD)),
           "order independent canonical serialization");
        reordered.setProperty("module.example.name","tampered");
        rejects(()->ConfigIntegrity.verify(reordered),"tamper detected");
        reordered.setProperty("module.example.name","okay");
        reordered.setProperty(ConfigIntegrity.FIELD,"ZZ");
        rejects(()->ConfigIntegrity.verify(reordered),"malformed hash detected");
        Random random=new Random(23818L);
        for(int n=0;n<3500;n++) {
            Properties p=new Properties();
            p.setProperty("schema","1");
            p.setProperty("module.id","ghostc-"+n);
            p.setProperty("metadata.key."+random.nextInt(20),
                "café-"+random.nextLong());
            ConfigIntegrity.seal(p);
            ok(ConfigIntegrity.verify(p),"random valid canonical config "+n);
            p.setProperty("module.id","modified-"+n);
            rejects(()->ConfigIntegrity.verify(p),"random tamper detected "+n);
        }
        Path folder=Files.createTempDirectory("ghostc-integrity-limits");
        Path tooBig=folder.resolve("large.properties");
        byte[] bytes=new byte[1024*1024+1];
        Files.write(tooBig,bytes);
        boolean oversize=false;
        try{ConfigIntegrity.read(tooBig);}catch(java.io.IOException expected){oversize=true;}
        ok(oversize,"size guard before properties load");
        // A schema-1 legacy file has no digest; its field count must still
        // respect the same hard cap as modern sealed configurations.
        Properties fieldBomb=new Properties();
        fieldBomb.setProperty("schema","1");
        for(int i=0;i<4100;i++)
            fieldBomb.setProperty("legacy.field."+i,"v");
        Path bombFile=folder.resolve("legacy-field-bomb.properties");
        write(bombFile,fieldBomb);
        boolean tooManyFields=false;
        try{ConfigIntegrity.read(bombFile);}
        catch(IllegalArgumentException expected){tooManyFields=true;}
        ok(tooManyFields,"legacy field count is capped without checksum");
    }
    /** R2.84: a parseable legacy backup is not a verified recovery source.
     * Direct loads continue to support legacy schema-1 migration. */
    private static void recoveryNeedsSealedBackup() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-unsealed-recovery");
        Path primary=dir.resolve("candidate.properties");
        Path backup=dir.resolve("candidate.properties.bak");
        Random random=new Random(2842026L);
        for(int i=0;i<256;i++) {
            Properties good=new Properties();
            good.setProperty("schema","1");
            good.setProperty("key",Long.toString(random.nextLong()));
            ConfigIntegrity.seal(good);
            write(primary,good);
            Properties changed=new Properties();
            changed.putAll(good);
            changed.setProperty("key","tampered-"+i);
            write(primary,changed); // checksum now mismatches
            byte[] damaged=Files.readAllBytes(primary);
            Properties legacy=new Properties();
            legacy.putAll(good);
            legacy.remove(ConfigIntegrity.FIELD);
            write(backup,legacy);
            boolean refused=false;
            try{ConfigIntegrity.restoreBackup(primary);}
            catch(IllegalArgumentException expected){refused=true;}
            ok(refused,"unsealed schema-1 backup rejected on recovery "+i);
            ok(java.util.Arrays.equals(damaged,Files.readAllBytes(primary)),
                "rejected legacy backup does not replace damaged primary "+i);
            ok(!Files.exists(dir.resolve("candidate.properties.corrupt")),
                "rejected backup does not create evidence promotion "+i);
            ok(!ConfigIntegrity.verify(ConfigIntegrity.read(backup)),
                "legacy file remains readable for ordinary migration "+i);
            ConfigIntegrity.seal(legacy);
            write(backup,legacy);
            ConfigIntegrity.restoreBackup(primary);
            ok(ConfigIntegrity.verify(ConfigIntegrity.read(primary)),
                "sealed counterpart is accepted for restoration "+i);
            ok(Files.exists(dir.resolve("candidate.properties.corrupt")),
                "valid recovery preserves original damaged bytes "+i);
            Files.delete(dir.resolve("candidate.properties.corrupt"));
        }
        Path profilesDir=Files.createTempDirectory("ghostc-unsealed-profile-recovery");
        ModuleRegistry registry=new ModuleRegistry();
        registry.register(new Dummy("test.feature"),44,
            new Setting.Bool("safe",true));
        ProfileStore profiles=new ProfileStore(profilesDir,registry);
        profiles.save("default");
        profiles.save("default");
        Path profilePrimary=profilesDir.resolve("default.properties");
        Path profileBackup=profilesDir.resolve("default.properties.bak");
        Properties profileLegacy=read(profileBackup);
        profileLegacy.remove(ConfigIntegrity.FIELD);
        write(profileBackup,profileLegacy);
        Properties corruptProfile=read(profilePrimary);
        corruptProfile.setProperty("module.test.feature.key","12");
        write(profilePrimary,corruptProfile);
        byte[] originalProfile=Files.readAllBytes(profilePrimary);
        registry.bind("test.feature",31);
        boolean profileRejected=false;
        try{profiles.loadRecovering("default");}
        catch(IllegalArgumentException expected){profileRejected=true;}
        ok(profileRejected && registry.get("test.feature").keybind()==31,
            "unsealed profile backup cannot change live keybinds");
        ok(java.util.Arrays.equals(originalProfile,Files.readAllBytes(profilePrimary)) &&
           !Files.exists(profilesDir.resolve("default.properties.corrupt")),
            "refused profile recovery retains original bytes without promotion");
        // Normal legacy migration is unaffected when this file is primary.
        Path legacyProfile=profilesDir.resolve("legacy.properties");
        write(legacyProfile,profileLegacy);
        profiles.load("legacy");
        ok(registry.get("test.feature").keybind()==44,
            "ordinary load still accepts schema-1 unsealed profile");
        ConfigIntegrity.seal(profileLegacy);
        write(profileBackup,profileLegacy);
        ok(profiles.loadRecovering("default") &&
           registry.get("test.feature").keybind()==44,
            "verified profile backup recovers expected trusted binding");
        registry.shutdown();

        Path uiDir=Files.createTempDirectory("ghostc-unsealed-ui-recovery");
        UiState state=new UiState();
        UiSettingsStore store=new UiSettingsStore(uiDir,state);
        state.position(.21,.32);store.save();
        state.position(.71,.82);store.save();
        Path uiPrimary=uiDir.resolve("ui.properties");
        Path uiBackup=uiDir.resolve("ui.properties.bak");
        Properties uiLegacy=read(uiBackup);
        uiLegacy.remove(ConfigIntegrity.FIELD);
        write(uiBackup,uiLegacy);
        Properties corruptUi=read(uiPrimary);
        corruptUi.setProperty("hud.x","0.99");
        write(uiPrimary,corruptUi);
        byte[] oldUi=Files.readAllBytes(uiPrimary);
        state.position(.51,.62);
        boolean uiRejected=false;
        try{store.loadRecovering();}
        catch(IllegalArgumentException expected){uiRejected=true;}
        ok(uiRejected && state.hudX()==.51 && state.hudY()==.62,
            "unsealed UI backup cannot change live HUD anchors");
        ok(java.util.Arrays.equals(oldUi,Files.readAllBytes(uiPrimary)) &&
           !Files.exists(uiDir.resolve("ui.properties.corrupt")),
            "refused UI recovery preserves corrupted primary unchanged");
        write(uiPrimary,uiLegacy);
        store.load();
        ok(state.hudX()==.21 && state.hudY()==.32,
            "ordinary UI load still accepts unsealed legacy settings");
        write(uiPrimary,corruptUi);
        ConfigIntegrity.seal(uiLegacy);
        write(uiBackup,uiLegacy);
        ok(store.loadRecovering() && state.hudX()==.21 && state.hudY()==.32,
            "sealed UI backup recovers exact expected HUD state");
    }
    private static void duplicateKeys() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-duplicate-properties");
        Path fixture=folder.resolve("duplicate.properties");
        // Java Properties would otherwise silently keep the last occurrence,
        // allowing a conflicting first value to evade canonical digest checks.
        Properties sealed=new Properties();
        sealed.setProperty("schema","1");
        sealed.setProperty("value","trusted");
        ConfigIntegrity.seal(sealed);
        Path valid=folder.resolve("valid.properties");
        write(valid,sealed);
        ok(ConfigIntegrity.verify(ConfigIntegrity.read(valid)),
            "unique modern sealed config remains readable");
        String saved=new String(Files.readAllBytes(valid),
            StandardCharsets.ISO_8859_1);
        String[] aliases={"value=conflict", "value : conflict",
            "v\\alue=conflict", "va\\lue=conflict",
            "value = trusted", "schema=1",
            "integrity.sha256=invalid"};
        for(int i=0;i<aliases.length;i++) {
            Files.write(fixture,(saved+"\n"+aliases[i]+"\n")
                .getBytes(StandardCharsets.ISO_8859_1));
            boolean refused=false;
            try{ConfigIntegrity.read(fixture);}
            catch(IllegalArgumentException expected){refused=true;}
            ok(refused,"duplicate decoded key rejected before checksum "+i);
        }
        // Even exact duplicate values and unsealed legacy records are ambiguous.
        Files.write(fixture,"schema=1\nschema=1\n".getBytes(
            StandardCharsets.ISO_8859_1));
        boolean legacyRefused=false;
        try{ConfigIntegrity.read(fixture);}
        catch(IllegalArgumentException expected){legacyRefused=true;}
        ok(legacyRefused,"duplicate legacy schema key rejected");
        Files.write(fixture,"schema=1\nvalue=unique\n".getBytes(
            StandardCharsets.ISO_8859_1));
        ok(!ConfigIntegrity.verify(ConfigIntegrity.read(fixture)),
            "unique legacy records remain readable");

        Random random=new Random(2782026L);
        for(int i=0;i<3000;i++) {
            String key="setting."+i;
            String value=Long.toString(random.nextLong());
            Properties p=new Properties();
            p.setProperty("schema","1");p.setProperty(key,value);
            ConfigIntegrity.seal(p);
            write(valid,p);
            String text=new String(Files.readAllBytes(valid),
                StandardCharsets.ISO_8859_1);
            ok(ConfigIntegrity.verify(ConfigIntegrity.read(valid)),
                "unique sealed randomized properties read "+i);
            String duplicate=(i%3==0?key+"="+value:
                i%3==1?key+" : conflict":key+" = other");
            Files.write(fixture,(text+"\n"+duplicate+"\n")
                .getBytes(StandardCharsets.ISO_8859_1));
            boolean rejected=false;
            try{ConfigIntegrity.read(fixture);}
            catch(IllegalArgumentException expected){rejected=true;}
            ok(rejected,"random conflicting/exact duplicate rejected "+i);
        }

        ModuleRegistry registry=new ModuleRegistry();
        registry.register(new Dummy("test.feature"),44,
            new Setting.Bool("safe",true));
        ProfileStore profile=new ProfileStore(folder,registry);
        profile.save("default");
        profile.save("default");
        Path primary=folder.resolve("default.properties");
        Path backup=folder.resolve("default.properties.bak");
        byte[] knownGood=Files.readAllBytes(backup);
        byte[] original=Files.readAllBytes(primary);
        Files.write(primary,"\nmodule.test.feature.key=44\n".getBytes(
            StandardCharsets.ISO_8859_1),java.nio.file.StandardOpenOption.APPEND);
        registry.bind("test.feature",31);
        ok(profile.loadRecovering("default"),
            "duplicate-key primary invokes verified profile backup recovery");
        ok(registry.get("test.feature").keybind()==44,
            "backup recovers trusted profile without accepting duplicate field");
        ok(java.util.Arrays.equals(knownGood,Files.readAllBytes(backup)),
            "profile backup is not overwritten on duplicate-key recovery");
        ok(Files.size(folder.resolve("default.properties.corrupt"))>original.length,
            "conflicting original preserved as corrupt evidence");
        registry.shutdown();

        UiState state=new UiState();
        UiSettingsStore store=new UiSettingsStore(folder,state);
        state.position(.22,.31);store.save();
        state.position(.82,.91);store.save();
        Path uiPrimary=folder.resolve("ui.properties");
        byte[] uiBackup=Files.readAllBytes(folder.resolve("ui.properties.bak"));
        Files.write(uiPrimary,"\nhud.x=0.99\n".getBytes(
            StandardCharsets.ISO_8859_1),java.nio.file.StandardOpenOption.APPEND);
        state.position(.55,.65);
        ok(store.loadRecovering(),
            "duplicate-key UI primary restores typed-validated backup");
        ok(state.hudX()==.22 && state.hudY()==.31,
            "UI recovery restores backup only, not conflicting shadow value");
        ok(java.util.Arrays.equals(uiBackup,
            Files.readAllBytes(folder.resolve("ui.properties.bak"))),
            "last trusted UI backup remains intact");
    }
    private static void profileRecovery() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-profile-restore");
        ModuleRegistry registry=new ModuleRegistry();
        Setting.Bool safe=new Setting.Bool("safe",true);
        registry.register(new Dummy("test.feature"),44,safe);
        ProfileStore store=new ProfileStore(folder,registry);
        registry.setEnabled("test.feature",true);
        store.save("default");
        Path primary=folder.resolve("default.properties"), backup=folder.resolve("default.properties.bak");
        ok(ConfigIntegrity.verify(read(primary)),"first profile sealed");
        safe.setFromText("false");
        store.save("default");
        ok(Files.exists(backup),"backup retained");
        ok(ConfigIntegrity.verify(read(backup)),"backup digest valid");
        Properties tampered=read(primary);
        tampered.setProperty("module.test.feature.key","88");
        write(primary,tampered); // no reseal: syntactically plausible corruption
        byte[] damaged=Files.readAllBytes(primary);
        boolean mismatch=false;
        try{ConfigIntegrity.verify(read(primary));}catch(IllegalArgumentException expected){mismatch=true;}
        ok(mismatch,"syntactically valid damaged profile fails checksum");
        registry.setEnabled("test.feature",false);
        safe.setFromText("false");
        registry.bind("test.feature",21);
        ok(store.loadRecovering("default"),"backup accepted after corrupt primary");
        ok(registry.get("test.feature").state()==ModuleRegistry.State.ENABLED,
           "profile backup restored enabled module");
        ok(registry.get("test.feature").keybind()==44,
           "profile backup restored original key");
        ok(safe.get(),"profile backup restored older setting");
        ok(ConfigIntegrity.verify(read(primary)),"repaired primary verifies");
        ok(java.util.Arrays.equals(damaged,Files.readAllBytes(
            folder.resolve("default.properties.corrupt"))),
            "corrupt original preserved byte for byte");
        ok(ConfigIntegrity.verify(read(backup)),"backup not overwritten by recovery");
        ok(!store.loadRecovering("default"),"healthy file no recovery");
        store.save("default");
        ok(ConfigIntegrity.verify(read(backup)),"subsequent save rotates healthy primary");
        // Even syntactically valid truncated modern files fail checksum preflight.
        Properties cut=read(primary);
        cut.remove("module.test.feature.enabled");
        write(primary,cut);
        registry.bind("test.feature",32);
        boolean rejected=false;
        try{store.load("default");}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"partial modern profile strictly rejected");
        ok(registry.get("test.feature").keybind()==32,
           "failed strict load cannot change live state");
        registry.shutdown();
    }
    /** R2.81: absent HUD widget axes must resolve from immutable defaults,
     * not whatever values were last edited in the live UI model. */
    private static void legacyWidgetDefaults() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-legacy-widget-anchors");
        UiState state=new UiState();
        UiSettingsStore store=new UiSettingsStore(folder,state);
        Path primary=folder.resolve("ui.properties");
        Properties legacy=new Properties();
        legacy.setProperty("schema","1");
        legacy.setProperty("layout","COMPACT");
        legacy.setProperty("theme","GRAPHITE");
        legacy.setProperty("hud.x","0.34");
        legacy.setProperty("hud.y","0.27");
        legacy.setProperty(HudPositions.property(HudPositions.Widget.CPS,true),"0.37");
        legacy.setProperty(HudPositions.property(HudPositions.Widget.TARGET,false),"0.43");
        write(primary,legacy);
        HudPositions defaults=new HudPositions();
        Random random=new Random(2812026L);
        for(int i=0;i<12000;i++) {
            for(HudPositions.Widget widget:HudPositions.Widget.values())
                state.widgets().position(widget,random.nextDouble(),random.nextDouble());
            state.position(random.nextDouble(),random.nextDouble());
            state.layout(UiState.Layout.WIDE);
            state.theme(UiState.Theme.INDIGO);
            store.load();
            ok(state.layout()==UiState.Layout.COMPACT &&
               state.theme()==UiState.Theme.GRAPHITE &&
               state.hudX()==0.34 && state.hudY()==0.27,
               "legacy status/layout restores identically "+i);
            for(HudPositions.Widget widget:HudPositions.Widget.values()) {
                double expectedX=widget==HudPositions.Widget.CPS?
                    0.37:defaults.x(widget);
                double expectedY=widget==HudPositions.Widget.TARGET?
                    0.43:defaults.y(widget);
                ok(Double.compare(state.widgets().x(widget),expectedX)==0 &&
                   Double.compare(state.widgets().y(widget),expectedY)==0,
                   "missing widget axis uses immutable default "+widget+"/"+i);
            }
        }
        ok(!ConfigIntegrity.verify(ConfigIntegrity.read(primary)),
            "unique unsealed legacy config remains readable");
        ConfigIntegrity.seal(legacy);
        write(primary,legacy);
        for(HudPositions.Widget widget:HudPositions.Widget.values())
            state.widgets().position(widget,0.12,0.23);
        store.load();
        ok(state.widgets().x(HudPositions.Widget.KEYSTROKES)==
           defaults.x(HudPositions.Widget.KEYSTROKES) &&
           state.widgets().y(HudPositions.Widget.CPS)==
           defaults.y(HudPositions.Widget.CPS),
           "sparse sealed file has the same default anchors");
        store.save();
        Path backup=folder.resolve("ui.properties.bak");
        ok(Files.exists(backup) && ConfigIntegrity.verify(ConfigIntegrity.read(backup)),
            "verified sparse primary accepted for safe backup rotation");
        Properties damaged=read(primary);
        damaged.setProperty("hud.x","0.99");
        write(primary,damaged);
        for(HudPositions.Widget widget:HudPositions.Widget.values())
            state.widgets().position(widget,0.61,0.72);
        state.position(.81,.91);
        ok(store.loadRecovering(),"damaged primary recovers sparse sealed backup");
        ok(state.hudX()==0.34 && state.hudY()==0.27,
            "recovery uses original status coordinates");
        for(HudPositions.Widget widget:HudPositions.Widget.values()) {
            double x=widget==HudPositions.Widget.CPS?0.37:defaults.x(widget);
            double y=widget==HudPositions.Widget.TARGET?0.43:defaults.y(widget);
            ok(Double.compare(state.widgets().x(widget),x)==0 &&
               Double.compare(state.widgets().y(widget),y)==0,
               "backup recovery does not inherit stale live widget "+widget);
        }
        ok(Files.exists(folder.resolve("ui.properties.corrupt")) &&
           ConfigIntegrity.verify(ConfigIntegrity.read(primary)),
           "corrupt primary retained while exact backup contents restored");
        ok(!store.loadRecovering(),"second load of repaired sparse file succeeds");
    }
    private static void uiRecovery() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-ui-recovery");
        UiState state=new UiState();
        UiSettingsStore store=new UiSettingsStore(folder,state);
        state.position(.2,.4);
        store.save();
        state.position(.8,.9);
        state.layout(UiState.Layout.COMPACT);
        store.save();
        Path primary=folder.resolve("ui.properties");
        Path backup=folder.resolve("ui.properties.bak");
        ok(ConfigIntegrity.verify(read(backup)),"UI backup sealed");
        Properties changed=read(primary);
        changed.setProperty("hud.target.y","0.4");
        write(primary,changed);
        byte[] damaged=Files.readAllBytes(primary);
        state.position(.6,.6);
        state.layout(UiState.Layout.COMPACT);
        ok(store.loadRecovering(),"UI fallback recovered damaged current config");
        ok(state.layout()==UiState.Layout.WIDE &&
           state.hudX()==.2 && state.hudY()==.4,
           "UI backup restores complete older state");
        ok(java.util.Arrays.equals(damaged,Files.readAllBytes(
            folder.resolve("ui.properties.corrupt"))),
           "UI original preserved on recover");
        ok(ConfigIntegrity.verify(read(primary)) && ConfigIntegrity.verify(read(backup)),
           "both primary and backup valid after recovery");
        ok(!store.loadRecovering(),"second startup reads valid primary");
        Properties legacy=new Properties();
        legacy.setProperty("schema","1");
        legacy.setProperty("layout","COMPACT");
        legacy.setProperty("theme","GRAPHITE");
        legacy.setProperty("hud.x","0.15");
        legacy.setProperty("hud.y","0.7");
        write(primary,legacy);
        store.load();
        ok(state.layout()==UiState.Layout.COMPACT && state.hudX()==.15,
           "legacy schema1 UI remains supported");
        ok(!ConfigIntegrity.verify(legacy),"unsealed legacy input recognized");
        // Corrupt both files: never claim recovery and never replace primary.
        store.save();
        store.save();
        Properties bad=read(primary);
        bad.setProperty("hud.x","NaN");
        write(primary,bad);
        Properties badBackup=read(backup);
        badBackup.setProperty("hud.y","NaN");
        write(backup,badBackup);
        byte[] oldPrimary=Files.readAllBytes(primary);
        boolean fail=false;
        try{store.loadRecovering();}catch(IllegalArgumentException expected){fail=true;}
        ok(fail,"both invalid configurations report failure");
        ok(java.util.Arrays.equals(oldPrimary,Files.readAllBytes(primary)),
           "no replacement from corrupt backup");
    }
    private static void rejectUnusableRecoveryWithoutMutation() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-r26-typed-recovery");
        ModuleRegistry registry=new ModuleRegistry();
        Setting.Bool safe=new Setting.Bool("safe",true);
        registry.register(new Dummy("test.feature"),44,safe);
        ProfileStore profiles=new ProfileStore(folder,registry);
        registry.setEnabled("test.feature",true);
        profiles.save("default");
        safe.setFromText("false");
        profiles.save("default");
        Path primary=folder.resolve("default.properties");
        Path backup=folder.resolve("default.properties.bak");
        Properties corrupt=read(primary);
        corrupt.setProperty("module.test.feature.key","11");
        write(primary,corrupt); // damaged seal on primary
        byte[] originalPrimary=Files.readAllBytes(primary);
        Properties sealedButUnusable=read(backup);
        sealedButUnusable.setProperty("module.test.feature.key","65"); // reserved F7
        ConfigIntegrity.seal(sealedButUnusable);
        write(backup,sealedButUnusable);
        byte[] invalidBackup=Files.readAllBytes(backup);
        ok(ConfigIntegrity.verify(read(backup)),"typed-bad backup checksum is valid");
        registry.setEnabled("test.feature",false);
        registry.bind("test.feature",51);
        safe.setFromText("false");
        boolean rejected=false;
        try{profiles.loadRecovering("default");}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"typed-invalid sealed module backup rejected");
        ok(registry.get("test.feature").state()==ModuleRegistry.State.DISABLED &&
           registry.get("test.feature").keybind()==51 && !safe.get(),
           "failed profile recovery never modifies live registry");
        ok(java.util.Arrays.equals(originalPrimary,Files.readAllBytes(primary)),
           "failed profile recovery preserves original primary");
        ok(java.util.Arrays.equals(invalidBackup,Files.readAllBytes(backup)),
           "failed profile recovery does not mutate unusable backup");
        ok(!Files.exists(folder.resolve("default.properties.corrupt")),
           "failed preflight does not create false successful recovery artifact");
        registry.shutdown();

        Path uiFolder=Files.createTempDirectory("ghostc-r26-ui-recovery");
        UiState state=new UiState();
        UiSettingsStore ui=new UiSettingsStore(uiFolder,state);
        state.position(.2,.3); ui.save();
        state.position(.8,.9); ui.save();
        Path uiPrimary=uiFolder.resolve("ui.properties");
        Path uiBackup=uiFolder.resolve("ui.properties.bak");
        Properties damagedUI=read(uiPrimary);
        damagedUI.setProperty("hud.y","0.15");
        write(uiPrimary,damagedUI);
        byte[] originalUI=Files.readAllBytes(uiPrimary);
        Properties invalidUI=read(uiBackup);
        invalidUI.setProperty("layout","INVALID_LAYOUT");
        ConfigIntegrity.seal(invalidUI);
        write(uiBackup,invalidUI);
        byte[] invalidUIBackup=Files.readAllBytes(uiBackup);
        ok(ConfigIntegrity.verify(read(uiBackup)),"unloadable UI backup checksum is valid");
        state.position(.51,.57);
        state.layout(UiState.Layout.COMPACT);
        boolean rejectedUI=false;
        try{ui.loadRecovering();}
        catch(IllegalArgumentException expected){rejectedUI=true;}
        ok(rejectedUI,"typed-invalid sealed UI backup rejected");
        ok(state.layout()==UiState.Layout.COMPACT &&
           state.hudX()==.51 && state.hudY()==.57,
           "failed UI recovery cannot mutate in-memory preferences");
        ok(java.util.Arrays.equals(originalUI,Files.readAllBytes(uiPrimary)),
           "failed UI recovery preserves original primary");
        ok(java.util.Arrays.equals(invalidUIBackup,Files.readAllBytes(uiBackup)),
           "failed UI recovery preserves original invalid backup");
        ok(!Files.exists(uiFolder.resolve("ui.properties.corrupt")),
           "failed UI preflight does not create recovery artifact");
    }
    private static void hostStartup() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-startup-recover");
        HostRuntime host=new HostRuntime(folder);
        host.ui().position(.12,.33);
        host.persistUi();
        host.ui().position(.77,.88);
        host.persistUi();
        host.persist();
        host.persist();
        Path profile=folder.resolve("default.properties");
        Path ui=folder.resolve("ui.properties");
        host.close();
        Properties damaged=read(ui);
        damaged.setProperty("hud.x",".99");
        write(ui,damaged);
        damaged=read(profile);
        damaged.setProperty("module.hud.status.key","65");
        write(profile,damaged);
        HostRuntime loaded=new HostRuntime(folder);
        ok("UI preferences recovered from backup".equals(loaded.uiWarning()),
           "user informed about UI fallback");
        ok("Profile recovered from backup".equals(loaded.profileWarning()),
           "user informed about profile fallback");
        ok(ConfigIntegrity.verify(read(ui))&&ConfigIntegrity.verify(read(profile)),
           "startup repaired both primary configs");
        ok(loaded.ui().hudX()==.77,"UI backup applied after repeated saves");
        loaded.close();
    }
    public static void main(String[] args) throws Exception {
        checksum();
        recoveryNeedsSealedBackup();
        duplicateKeys();
        profileRecovery();
        uiRecovery();
        legacyWidgetDefaults();
        rejectUnusableRecoveryWithoutMutation();
        hostStartup();
        ok(count>7000,"meaningful integrity and recovery regression coverage");
        System.out.println("CONFIG_RECOVERY_PASS assertions="+count);
    }
}
