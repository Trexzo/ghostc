package dev.ghostfusion.tests;
import dev.ghostfusion.core.ConfigIntegrity;
import dev.ghostfusion.core.Setting;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.ui.UiState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.OutputStream;
import java.util.Properties;

/** Retired appearance IDs are read only by trusted storage migrations. */
public final class SlateLegacyMigrationTests {
    private static int checks;
    private static void ok(boolean b,String reason){
        checks++;if(!b)throw new AssertionError(reason);
    }
    private static void store(Path path,Properties values)throws Exception {
        ConfigIntegrity.seal(values);
        try(OutputStream out=Files.newOutputStream(path)){
            values.store(out,"Legacy visual migration fixture");
        }
    }
    public static void main(String[] args)throws Exception {
        Path dir=Files.createTempDirectory("ghostc-slate-legacy-");
        Properties ui=new Properties();
        ui.setProperty("schema","1");ui.setProperty("theme","NEBULA");
        ui.setProperty("layout","WIDE");
        ui.setProperty("hud.x","0.42");ui.setProperty("hud.y","0.36");
        store(dir.resolve("ui.properties"),ui);
        Properties old=new Properties();
        old.setProperty("schema","1");
        old.setProperty("module.hud.target.setting.skin","nebula");
        old.setProperty("module.hud.keystrokes.setting.appearance","nebula");
        old.setProperty("module.hud.cps.setting.appearance","nebula");
        old.setProperty("module.hud.modulelist.setting.appearance","nebula");
        store(dir.resolve("default.properties"),old);

        HostRuntime host=new HostRuntime(dir);
        ok(host.ui().theme()==UiState.Theme.SLATE,
           "old UI style becomes Slate");
        ok(host.ui().hudX()==0.42&&host.ui().hudY()==0.36,
           "saved HUD positions survive migration");
        ok("slate".equals(host.targetSkin())&&
           "slate".equals(host.keysAppearance())&&
           "slate".equals(host.cpsAppearance())&&
           "slate".equals(host.moduleListAppearance()),
           "all four old skin IDs map to Slate at profile read");
        ok(host.profileWarning().isEmpty()&&host.uiWarning().isEmpty(),
           "legacy profile loads without corruption warning");
        Setting.Choice choice=(Setting.Choice)host.modules().get("hud.target")
            .settings().get("skin");
        ok(choice.options().contains("slate")&&!choice.options().contains("nebula"),
           "no legacy selectable UI option");
        boolean rejected=false;
        try{choice.parse("nebula");}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"live choice rejects obsolete identifier");
        host.persist();host.persistUi();host.close();

        Properties migrated=ConfigIntegrity.read(dir.resolve("default.properties"));
        for(String path:new String[]{
            "module.hud.target.setting.skin",
            "module.hud.keystrokes.setting.appearance",
            "module.hud.cps.setting.appearance",
            "module.hud.modulelist.setting.appearance"
        })ok("slate".equals(migrated.getProperty(path)),
              "new save writes Slate for "+path);
        Properties migratedUi=ConfigIntegrity.read(dir.resolve("ui.properties"));
        ok("SLATE".equals(migratedUi.getProperty("theme")),
           "UI save writes new enum name");
        HostRuntime restored=new HostRuntime(dir);
        ok(restored.ui().theme()==UiState.Theme.SLATE&&
           "slate".equals(restored.targetSkin()),
           "roundtrip is stable");
        restored.close();
        System.out.println("SLATE_LEGACY_MIGRATION_PASS checks="+checks);
    }
}
