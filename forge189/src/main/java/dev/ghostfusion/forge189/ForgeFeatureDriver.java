package dev.ghostfusion.forge189;

import dev.ghostfusion.features.CpsMeter;
import dev.ghostfusion.ui.HudPositions.Widget;
import dev.ghostfusion.ui.HudFootprints;
import dev.ghostfusion.features.SprintPolicy;
import dev.ghostfusion.features.TargetHudPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import dev.ghostfusion.services.WorldSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.potion.Potion;
import org.lwjgl.input.Mouse;

/**
 * Only Forge-side bridge for first-party rendering/movement features.
 * Runs exclusively on the Minecraft client thread. Core modules never reference game classes.
 */
public final class ForgeFeatureDriver {
    private final Minecraft mc;
    private final HostRuntime host;
    private final CpsMeter cps = new CpsMeter();
    private final TargetHudPolicy targets = new TargetHudPolicy();
    private final PlayerEspRenderer esp;
    ForgeFeatureDriver(Minecraft mc, HostRuntime host) {
        this.mc = mc; this.host = host;
        esp = new PlayerEspRenderer(mc, host);
    }
    void tick() {
        if (mc.thePlayer == null || mc.theWorld == null ||
            mc.currentScreen != null || !mc.inGameHasFocus) return;
        host.runFeature("movement.sprint", new Runnable() {
            public void run() { sprintTick(); }
        });
    }
    private void sprintTick() {
        EntityPlayerSP player = mc.thePlayer;
        if (player.movementInput == null) return;
        boolean run = SprintPolicy.shouldSprint(
            true, true, player.onGround, player.isCollidedHorizontally,
            player.isSneaking(), player.isUsingItem(), player.movementInput.moveForward,
            player.getFoodStats().getFoodLevel(), player.capabilities.isFlying,
            player.isPotionActive(Potion.blindness),
            mc.gameSettings.keyBindSprint.isKeyDown());
        if (run) player.setSprinting(true);
        // Never force sprint OFF: vanilla/player key state remains authoritative.
    }
    void mouseInput() {
        final int button = Mouse.getEventButton();
        if (button != 0 && button != 1) return;
        boolean focused = mc.currentScreen == null && mc.inGameHasFocus && host.worldPresent();
        cps.mouse(button, Mouse.getEventButtonState(), System.nanoTime(), focused);
    }
    void reset() { cps.clear(); }
    void render3d(final float partialTicks) {
        host.runFeature("render.esp", new Runnable() {
            public void run() { esp.draw(partialTicks); }
        });
    }
    /** A separate guarded GL scope for every feature prevents a failed callback
     * from contaminating another feature's render state in the same frame. */
    private void drawFeature(String id, final Runnable painter) {
        host.runFeature(id, new Runnable() {
            public void run() { RenderStateGuard.draw(painter); }
        });
    }
    void render2d(final ScaledResolution resolution) {
        if (!host.worldPresent() || mc.thePlayer == null || mc.theWorld == null) return;
        final int sw=resolution.getScaledWidth(), sh=resolution.getScaledHeight();
        drawFeature("hud.keystrokes", new Runnable() {
            public void run() { drawKeys(sw,sh); }
        });
        drawFeature("hud.cps", new Runnable() {
            public void run() { drawCps(sw,sh); }
        });
        drawFeature("hud.target", new Runnable() {
            public void run() { drawTarget(sw,sh); }
        });
        drawFeature("hud.diagnostics", new Runnable() {
            public void run() { drawDiagnostics(sw,sh); }
        });
    }
    private String keyName(KeyBinding binding) {
        int code = binding.getKeyCode();
        if (code == 0) return "?";
        String display = GameSettings.getKeyDisplayString(code);
        return display == null || display.trim().isEmpty() ? "?" :
            display.toUpperCase(java.util.Locale.ROOT);
    }
    private void key(String label, int x,int y,int width,boolean pressed) {
        Gui.drawRect(x,y,x+width,y+17,pressed ? 0xCBA8A9FF : 0xB3171C30);
        // Allow remapped mouse/keyboard names without overflowing 25px cells.
        while(label.length()>1 && mc.fontRendererObj.getStringWidth(label)>width-5)
            label=label.substring(0,label.length()-1);
        int textWidth=mc.fontRendererObj.getStringWidth(label);
        mc.fontRendererObj.drawStringWithShadow(label,
            x+(width-textWidth)/2,y+5,pressed ? 0xFF121629 : 0xFFF0F0FF);
    }
    private void drawKeys(int sw,int sh) {
        int x=host.ui().widgets().pixelX(Widget.KEYSTROKES,sw,Widget.KEYSTROKES.width);
        int y=host.ui().widgets().pixelY(Widget.KEYSTROKES,sh,
            HudFootprints.height(Widget.KEYSTROKES,host.keysJump(),host.keysSneak(),host.targetHealth()));
        key(keyName(mc.gameSettings.keyBindForward),x+29,y,25,mc.gameSettings.keyBindForward.isKeyDown());
        key(keyName(mc.gameSettings.keyBindLeft),x,y+20,25,mc.gameSettings.keyBindLeft.isKeyDown());
        key(keyName(mc.gameSettings.keyBindBack),x+29,y+20,25,mc.gameSettings.keyBindBack.isKeyDown());
        key(keyName(mc.gameSettings.keyBindRight),x+58,y+20,25,mc.gameSettings.keyBindRight.isKeyDown());
        int row=y+40;
        if (host.keysJump()) {
            key(keyName(mc.gameSettings.keyBindJump),x,row,83,mc.gameSettings.keyBindJump.isKeyDown()); row+=20;
        }
        if (host.keysSneak()) key(keyName(mc.gameSettings.keyBindSneak),x,row,83,mc.gameSettings.keyBindSneak.isKeyDown());
    }
    private void drawCps(int sw,int sh) {
        int x=2+host.ui().widgets().pixelX(Widget.CPS,sw,Widget.CPS.width);
        int y=2+host.ui().widgets().pixelY(Widget.CPS,sh,Widget.CPS.height);
        int l=cps.left(System.nanoTime()), r=cps.right(System.nanoTime());
        String text=host.cpsRight() ? "L "+l+" | R "+r+" CPS" : "L "+l+" CPS";
        Gui.drawRect(x-2,y-2,x+101,y+13,0xBE131927);
        mc.fontRendererObj.drawStringWithShadow(text,x,y+1,0xFFE3E9FF);
    }
    private void drawDiagnostics(int sw,int sh) {
        int width=286, height=180;
        int x=Math.max(0,sw-width-4), y=4;
        Gui.drawRect(x,y,Math.min(sw,x+width),y+height,0xC31B2133);
        mc.fontRendererObj.drawStringWithShadow("GhostC " + GhostCMod.CLIENT_VERSION + " diagnostics",x+5,y+5,0xFFAEAAFF);
        String epoch="Epoch " + host.services().generation() + " | Tick " + host.services().tick();
        String gui="F7 "+host.diagnostics().count(Counter.GUI_OPEN)+
            " | GUI frames "+host.diagnostics().count(Counter.GUI_FRAME);
        String render="2D "+host.diagnostics().count(Counter.OVERLAY_FRAME)+
            " | World "+host.diagnostics().count(Counter.WORLD_RENDER_FRAME);
        String events="Keys "+host.diagnostics().count(Counter.KEY_CALLBACK)+
            " | Mouse "+host.diagnostics().count(Counter.MOUSE_CALLBACK);
        String queue="Action "+host.services().actions().pending()+
            " | Rotation "+host.services().rotations().pending();
        String applied="Aim "+host.diagnostics().count(Counter.AIM_APPLIED)+
            " | Attack "+host.diagnostics().count(Counter.EXTRA_ATTACK)+
            " | Use "+host.diagnostics().count(Counter.EXTRA_USE);
        String esp="ESP frames "+host.diagnostics().count(Counter.ESP_RENDER_FRAME)+
            " | Players "+host.diagnostics().count(Counter.ESP_DRAWN_PLAYER);
        String skeleton="Skeletons "+host.diagnostics().count(Counter.ESP_DRAWN_SKELETON)+
            " | Invalid "+host.diagnostics().count(Counter.ESP_INVALID_GEOMETRY);
        dev.ghostfusion.host.FramePerformance.Snapshot perf=host.performanceSnapshot();
        String fps="FPS min/p50/p95/p99 "+perf.minFps+"/"+perf.medianFps+
            "/"+perf.p95Fps+"/"+perf.p99Fps;
        String memory="Heap "+perf.heapMiB+"/"+perf.peakHeapMiB+
            " MiB | Frames "+perf.frames;
        dev.ghostfusion.host.SnapshotTelemetry.Snapshot snap=host.snapshotTelemetry();
        String snapTiming="Snapshot p50/p95/p99 "+snap.p50Us+"/"+snap.p95Us+
            "/"+snap.p99Us+" us";
        String snapVolume="Scanned "+snap.scanned+" | Range "+snap.inRange+
            " | LOS "+snap.losQueries;
        String snapOutput="Published "+snap.published+" | Ticks "+snap.samples+
            " | Max "+snap.maxUs+" us";
        dev.ghostfusion.host.SnapshotTelemetry.Mode mode=host.lastSnapshotMode();
        dev.ghostfusion.host.SnapshotTelemetry.Snapshot modeStats=host.snapshotTelemetry(mode);
        String snapClass="Last mode "+mode.name()+" p95 "+modeStats.p95Us+
            " us | n "+modeStats.samples;
        mc.fontRendererObj.drawStringWithShadow(epoch,x+5,y+17,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(gui,x+5,y+28,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(render,x+5,y+39,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(events,x+5,y+50,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(queue,x+5,y+61,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(applied,x+5,y+72,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(esp,x+5,y+83,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(skeleton,x+5,y+94,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(fps,x+5,y+105,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(memory,x+5,y+116,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(snapTiming,x+5,y+127,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(snapVolume,x+5,y+138,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(snapOutput,x+5,y+149,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(snapClass,x+5,y+160,0xFFC9D1DF);
        // Focus/suppression remains in the saved local report.
    }
    private void drawTarget(int sw,int sh) {
        WorldSnapshot w=host.services().snapshot();
        WorldSnapshot.Entity target=targets.select(w,host.targetRange(),host.targetVisible());
        if (target==null) return;
        Entity e=mc.theWorld.getEntityByID(target.id);
        if (!(e instanceof EntityPlayer) || e == mc.thePlayer) return;
        EntityPlayer player=(EntityPlayer)e;
        String name=player.getName();
        if (name == null || name.length() > 26) name="Player #"+target.id;
        int width=HudFootprints.width(Widget.TARGET);
        int height=HudFootprints.height(Widget.TARGET,host.keysJump(),
            host.keysSneak(),host.targetHealth());
        int x=host.ui().widgets().pixelX(Widget.TARGET,sw,width);
        int y=host.ui().widgets().pixelY(Widget.TARGET,sh,height);
        Gui.drawRect(x,y,x+width,y+height,0xD0141A2B);
        Gui.drawRect(x,y,x+2,y+height,0xFF9A8CFF);
        mc.fontRendererObj.drawStringWithShadow(name,x+9,y+7,0xFFF1F2FF);
        if(host.targetHealth()) {
            double max=Math.max(1.0,player.getMaxHealth());
            double fraction=Math.max(0,Math.min(1.0,target.health/max));
            Gui.drawRect(x+9,y+25,x+width-9,y+31,0xFF414B60);
            Gui.drawRect(x+9,y+25,x+9+(int)((width-18)*fraction),y+31,0xFF9A8CFF);
            mc.fontRendererObj.drawStringWithShadow(
                String.format(java.util.Locale.ROOT,"%.1f / %.1f",target.health,max),
                x+width-70,y+8,0xFFB6C2DD);
        }
    }
}
