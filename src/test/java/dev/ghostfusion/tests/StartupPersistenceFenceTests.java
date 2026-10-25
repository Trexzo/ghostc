package dev.ghostfusion.tests;

import dev.ghostfusion.core.ConfigIntegrity;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.host.HostRuntime;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

/**
 * R3.20: failed startup recovery must not destroy the unreadable primary or
 * backup when normal runtime events trigger automatic persistence.
 */
public final class StartupPersistenceFenceTests {
    private static int count;
    private static void ok(boolean value,String why) {
        count++;
        if(!value)throw new AssertionError(why);
    }
    private static byte[] invalidProfile(int i) {
        return ("schema=1\nmodule.hud.status.key=" +
            (i%2==0?"not-a-key":"-5") + "\n# profile original "+i+"\n")
            .getBytes(StandardCharsets.UTF_8);
    }
    private static byte[] invalidUi(int i) {
        return ("schema=1\ntheme=" +
            (i%2==0?"BAD_THEME":"INDIGO") + "\nhud.x="+
            (i%2==0?"0.25":"NaN")+"\n# UI original "+i+"\n")
            .getBytes(StandardCharsets.UTF_8);
    }
    private static void preserved(Path file,byte[] bytes,String message)
            throws Exception {
        ok(Arrays.equals(bytes,Files.readAllBytes(file)),message);
    }
    private static void badProfileDoesNotEraseOnClose() throws Exception {
        for(int i=0;i<96;i++) {
            Path folder=Files.createTempDirectory("ghostc-r320-bad-profile");
            Path profile=folder.resolve("default.properties");
            byte[] original=invalidProfile(i);
            Files.write(profile,original);
            HostRuntime host=new HostRuntime(folder);
            ok(host.profileWarning().contains("save blocked"),
                "bad profile warning identifies persistent fence "+i);
            ok(host.uiWarning().isEmpty(),
                "valid/absent UI remains independently healthy "+i);
            ok(host.modules().get("hud.status").state()==ModuleRegistry.State.ENABLED,
                "fallback keeps default status module usable "+i);
            host.modules().setEnabled("hud.metrics",true);
            host.persist(); // explicit UI action
            preserved(profile,original,"explicit profile persist did not erase error "+i);
            host.ui().position(.32,.45);
            host.persistUi();
            ok(Files.isRegularFile(folder.resolve("ui.properties")),
                "healthy UI still saved despite bad profile "+i);
            host.close();
            preserved(profile,original,"close did not overwrite bad profile "+i);
            ok(!Files.exists(folder.resolve("default.properties.bak")),
                "no false clean profile backup after refused load "+i);
            ok(!Files.exists(folder.resolve("default.properties.corrupt")),
                "unrecovered profile not mislabeled as restored "+i);
            ok(host.profileWarning().contains("save blocked"),
                "load warning persists through close "+i);
        }
    }
    private static void badUiDoesNotEraseOnClose() throws Exception {
        for(int i=0;i<96;i++) {
            Path folder=Files.createTempDirectory("ghostc-r320-bad-ui");
            Path ui=folder.resolve("ui.properties");
            byte[] original=invalidUi(i);
            Files.write(ui,original);
            HostRuntime host=new HostRuntime(folder);
            ok(host.uiWarning().contains("save blocked"),
                "bad UI warning exposes persistence fence "+i);
            ok(host.profileWarning().isEmpty(),
                "healthy/absent profile not blocked "+i);
            host.ui().position(.55,.66);
            host.persistUi(); // explicit drag or reset UI action
            preserved(ui,original,"UI edit did not erase unreadable bytes "+i);
            host.modules().setEnabled("hud.metrics",true);
            host.persist();
            ok(Files.isRegularFile(folder.resolve("default.properties")),
                "healthy module profile can be saved independently "+i);
            host.close();
            preserved(ui,original,"close did not overwrite bad UI "+i);
            ok(!Files.exists(folder.resolve("ui.properties.bak")),
                "unrecoverable UI did not fabricate a trusted backup "+i);
            ok(host.uiWarning().contains("save blocked"),
                "UI warning retained after shutdown "+i);
        }
    }
    private static void bothFilesAndFailedBackupsStayByteExact() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-r320-both-unrecoverable");
        Path p=folder.resolve("default.properties");
        Path u=folder.resolve("ui.properties");
        Path pb=folder.resolve("default.properties.bak");
        Path ub=folder.resolve("ui.properties.bak");
        byte[] p0=invalidProfile(9),u0=invalidUi(9);
        byte[] pb0="schema=1\nmodule.hud.status.key=not-a-key\n".getBytes("UTF-8");
        byte[] ub0="schema=1\nhud.y=9.0\n".getBytes("UTF-8");
        Files.write(p,p0);Files.write(u,u0);
        Files.write(pb,pb0);Files.write(ub,ub0);
        HostRuntime host=new HostRuntime(folder);
        ok(host.profileWarning().contains("save blocked") &&
           host.uiWarning().contains("save blocked"),
           "each failed primary/backup pair fences its own saver");
        host.persist();host.persistUi();host.close();
        preserved(p,p0,"profile primary preserved byte exact");
        preserved(u,u0,"UI primary preserved byte exact");
        preserved(pb,pb0,"profile backup preserved byte exact");
        preserved(ub,ub0,"UI backup preserved byte exact");
        ok(!Files.exists(folder.resolve("default.properties.corrupt")) &&
           !Files.exists(folder.resolve("ui.properties.corrupt")),
           "failed backup admission never claims restored evidence");
    }
    private static void verifiedBackupRecoveryRemainsWritable() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-r320-successful-recovery");
        HostRuntime first=new HostRuntime(folder);
        first.persist();first.persist(); // promote sealed last-known-good backup
        first.ui().position(.23,.34);
        first.persistUi();first.persistUi();
        first.close();
        Path p=folder.resolve("default.properties"),u=folder.resolve("ui.properties");
        ok(Files.exists(folder.resolve("default.properties.bak")) &&
           Files.exists(folder.resolve("ui.properties.bak")),
           "verified backups exist before fault injection");
        byte[] profile=Files.readAllBytes(p);
        byte[] layout=Files.readAllBytes(u);
        Files.write(p,(new String(profile,StandardCharsets.ISO_8859_1)+
            "\nmodule.hud.status.key=invalid-shadow\n").getBytes(StandardCharsets.ISO_8859_1));
        Files.write(u,(new String(layout,StandardCharsets.ISO_8859_1)+
            "\nhud.x=1.10\n").getBytes(StandardCharsets.ISO_8859_1));
        HostRuntime recovered=new HostRuntime(folder);
        ok("Profile recovered from backup".equals(recovered.profileWarning()),
            "verified backup restores profile without write fence");
        ok("UI preferences recovered from backup".equals(recovered.uiWarning()),
            "verified backup restores UI without write fence");
        recovered.modules().setEnabled("hud.metrics",true);
        recovered.persist();
        recovered.ui().position(.67,.78);
        recovered.persistUi();
        recovered.close();
        HostRuntime resumed=new HostRuntime(folder);
        ok(resumed.profileWarning().isEmpty() && resumed.uiWarning().isEmpty(),
            "successfully recovered configs accept and reopen changed saves");
        ok(resumed.modules().get("hud.metrics").state()==ModuleRegistry.State.ENABLED &&
           resumed.ui().hudX()==.67 && resumed.ui().hudY()==.78,
           "real independent module/UI changes persisted after successful recovery");
        resumed.close();
        ok(Files.isRegularFile(folder.resolve("default.properties.corrupt")) &&
           Files.isRegularFile(folder.resolve("ui.properties.corrupt")),
           "original failed primaries retained by verified backup recovery");
    }
    public static void main(String[] args) throws Exception {
        badProfileDoesNotEraseOnClose();
        badUiDoesNotEraseOnClose();
        bothFilesAndFailedBackupsStayByteExact();
        verifiedBackupRecoveryRemainsWritable();
        ok(count>=700,"enough independent startup preservation assertions");
        System.out.println("STARTUP_PERSISTENCE_FENCE_PASS assertions="+count);
    }
}
