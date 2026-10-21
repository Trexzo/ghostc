package dev.ghostfusion.forge189;

import dev.ghostfusion.features.CpsMeter;
import dev.ghostfusion.ui.HudPositions.Widget;
import dev.ghostfusion.ui.HudFootprints;
import dev.ghostfusion.ui.HudWidgetViewport;
import dev.ghostfusion.ui.DiagnosticsHudLayout;
import dev.ghostfusion.ui.HudTextFit;
import dev.ghostfusion.features.SprintPolicy;
import dev.ghostfusion.features.TargetHudPolicy;
import dev.ghostfusion.features.HudLiveTargetGate;
import dev.ghostfusion.features.TargetHudTextLayout;
import dev.ghostfusion.features.TargetHudPalette;
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
    // Reuse callback while measuring with the currently active MC font.
    private final java.util.function.ToIntFunction<String> targetFontWidths;
    ForgeFeatureDriver(Minecraft mc, HostRuntime host) {
        this.mc = mc; this.host = host;
        targetFontWidths = new java.util.function.ToIntFunction<String>() {
            public int applyAsInt(String value) {
                return ForgeFeatureDriver.this.mc.fontRendererObj.getStringWidth(value);
            }
        };
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
        // Measure Minecraft's active font and clip on Unicode boundaries.
        String fitted=HudTextFit.fit(label,Math.max(0,width-5),targetFontWidths);
        if(fitted.isEmpty())return;
        int textWidth=mc.fontRendererObj.getStringWidth(fitted);
        mc.fontRendererObj.drawStringWithShadow(fitted,
            x+(width-textWidth)/2,y+5,pressed ? 0xFF121629 : 0xFFF0F0FF);
    }
    private void drawKeys(int sw,int sh) {
        // Do not draw a partial Keystrokes grid into a smaller scaled viewport.
        HudWidgetViewport.Frame frame=HudWidgetViewport.place(
            host.ui().widgets(),Widget.KEYSTROKES,sw,sh,
            host.keysJump(),host.keysSneak(),host.targetHealth());
        if(frame==null)return;
        int x=frame.x,y=frame.y;
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
        // Clamp the legacy 103x17 card when scaled windows are unusually small.
        int right=Math.min(sw,x+101),bottom=Math.min(sh,y+15);
        int textBudget=right-x-2;
        if(textBudget<=0 || bottom-y-1<mc.fontRendererObj.FONT_HEIGHT)return;
        long now=System.nanoTime();
        int l=cps.left(now),r=cps.right(now);
        String text=HudTextFit.cps(l,r,host.cpsRight(),textBudget,targetFontWidths);
        Gui.drawRect(x-2,y-2,right,bottom,0xBE131927);
        if(!text.isEmpty())
            mc.fontRendererObj.drawStringWithShadow(text,x,y+1,0xFFE3E9FF);
    }
    private void drawDiagnosticText(String value,int x,int y,int maxWidth,int color) {
        String fitted=mc.fontRendererObj.trimStringToWidth(value,maxWidth);
        if(!fitted.isEmpty())
            mc.fontRendererObj.drawStringWithShadow(fitted,x,y,color);
    }
    /** Rendered-row-only metric acquisition. Snapshot distributions are read
     * once per visible panel rather than once per string or invisible row. */
    private final class DiagnosticLines {
        private dev.ghostfusion.host.FramePerformance.Snapshot performance;
        private dev.ghostfusion.host.SnapshotTelemetry.Snapshot snapshot;
        private dev.ghostfusion.host.FramePerformance.Snapshot performance(){
            if(performance==null)performance=host.performanceSnapshot();
            return performance;
        }
        private dev.ghostfusion.host.SnapshotTelemetry.Snapshot snapshot(){
            if(snapshot==null)snapshot=host.snapshotTelemetry();
            return snapshot;
        }
        String row(int index) {
            switch(index) {
                case 0:
                    return "Epoch " + host.services().generation() + " | Tick " + host.services().tick();
                case 1:
                    return "F7 "+host.diagnostics().count(Counter.GUI_OPEN)+
                        " | GUI frames "+host.diagnostics().count(Counter.GUI_FRAME);
                case 2:
                    return "2D "+host.diagnostics().count(Counter.OVERLAY_FRAME)+
                        " | World "+host.diagnostics().count(Counter.WORLD_RENDER_FRAME);
                case 3:
                    return "Keys "+host.diagnostics().count(Counter.KEY_CALLBACK)+
                        " | Mouse "+host.diagnostics().count(Counter.MOUSE_CALLBACK);
                case 4:
                    return "Action "+host.services().actions().pending()+
                        " | Rotation "+host.services().rotations().pending();
                case 5:
                    return "Aim "+host.diagnostics().count(Counter.AIM_APPLIED)+
                        " | Attack "+host.diagnostics().count(Counter.EXTRA_ATTACK)+
                        " | Use "+host.diagnostics().count(Counter.EXTRA_USE);
                case 6:
                    return "ESP frames "+host.diagnostics().count(Counter.ESP_RENDER_FRAME)+
                        " | Players "+host.diagnostics().count(Counter.ESP_DRAWN_PLAYER);
                case 7:
                    return "Skeletons "+host.diagnostics().count(Counter.ESP_DRAWN_SKELETON)+
                        " | Invalid "+host.diagnostics().count(Counter.ESP_INVALID_GEOMETRY);
                case 8: {
                    dev.ghostfusion.host.FramePerformance.Snapshot perf=performance();
                    return "FPS min/p50/p95/p99 "+perf.minFps+"/"+perf.medianFps+
                        "/"+perf.p95Fps+"/"+perf.p99Fps;
                }
                case 9: {
                    dev.ghostfusion.host.FramePerformance.Snapshot perf=performance();
                    return "Heap "+perf.heapMiB+"/"+perf.peakHeapMiB+
                        " MiB | Frames "+perf.frames;
                }
                case 10: {
                    dev.ghostfusion.host.SnapshotTelemetry.Snapshot snap=snapshot();
                    return "Snapshot p50/p95/p99 "+snap.p50Us+"/"+snap.p95Us+
                        "/"+snap.p99Us+" us";
                }
                case 11: {
                    dev.ghostfusion.host.SnapshotTelemetry.Snapshot snap=snapshot();
                    return "Scanned "+snap.scanned+" | Range "+snap.inRange+
                        " | LOS "+snap.losQueries;
                }
                case 12: {
                    dev.ghostfusion.host.SnapshotTelemetry.Snapshot snap=snapshot();
                    return "Published "+snap.published+" | Ticks "+snap.samples+
                        " | Max "+snap.maxUs+" us";
                }
                case 13: {
                    dev.ghostfusion.host.SnapshotTelemetry.Mode mode=host.lastSnapshotMode();
                    dev.ghostfusion.host.SnapshotTelemetry.Snapshot modeStats=host.snapshotTelemetry(mode);
                    return "Last mode "+mode.name()+" p95 "+modeStats.p95Us+
                        " us | n "+modeStats.samples;
                }
                default: throw new IllegalArgumentException("Invalid diagnostics row");
            }
        }
    }
    private void drawDiagnostics(int sw,int sh) {
        DiagnosticsHudLayout layout=DiagnosticsHudLayout.calculate(
            sw,sh,mc.fontRendererObj.FONT_HEIGHT);
        if(layout.rows==0)return;
        int x=layout.x, y=layout.y;
        Gui.drawRect(x,y,x+layout.width,y+layout.height,0xC31B2133);
        drawDiagnosticText("GhostC " + GhostCMod.CLIENT_VERSION +
            " diagnostics",x+5,y+5,layout.textWidth,0xFFAEAAFF);
        // Compact panels evaluate ONLY the rows the layout can actually paint.
        // Full panels retain exactly the original fourteen labels and order.
        DiagnosticLines lines=new DiagnosticLines();
        for(int slot=0;slot<layout.rows;slot++)
            drawDiagnosticText(lines.row(layout.sourceRow(slot)),x+5,
                layout.rowY(slot),layout.textWidth,0xFFC9D1DF);
        // Focus/suppression remains in the saved local report.
    }
    private void drawTarget(int sw,int sh) {
        final boolean health=host.targetHealth();
        // Skip target selection/world lookup when no full card can be shown.
        HudWidgetViewport.Frame frame=HudWidgetViewport.place(
            host.ui().widgets(),Widget.TARGET,sw,sh,
            host.keysJump(),host.keysSneak(),health);
        if(frame==null)return;
        WorldSnapshot w=host.services().snapshot();
        WorldSnapshot.Entity target=targets.select(w,host.targetRange(),host.targetVisible());
        if (target==null) return;
        Entity e=mc.theWorld.getEntityByID(target.id);
        if (!(e instanceof EntityPlayer) || e == mc.thePlayer) return;
        EntityPlayer player=(EntityPlayer)e;
        // A tick snapshot selects a stable ID, but health/death/invisibility
        // may change before this overlay callback. Never paint stale state.
        final double liveHealth=player.getHealth();
        if(!HudLiveTargetGate.eligible(player,mc.thePlayer,player.isDead,
            liveHealth,player.isInvisible(),host.targetVisible()))return;
        // The owner-tick snapshot is only a selection hint; a player or
        // local camera may have left the selected range before this frame.
        if(!HudLiveTargetGate.withinRange(
            player.posX,player.posY,player.posZ,
            mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ,
            host.targetRange()))return;
        String name=player.getName();
        int width=frame.width,height=frame.height;
        int x=frame.x,y=frame.y;
        TargetHudTextLayout.Result labels=TargetHudTextLayout.layout(
            name,target.id,health,host.targetHealthText(),liveHealth,
            player.getMaxHealth(),width,targetFontWidths);
        TargetHudPalette palette=TargetHudPalette.of(host.targetSkin());
        Gui.drawRect(x,y,x+width,y+height,palette.background);
        if(palette.outline!=0) {
            Gui.drawRect(x,y,x+width,y+1,palette.outline);
            Gui.drawRect(x,y+height-1,x+width,y+height,palette.outline);
            Gui.drawRect(x+width-1,y,x+width,y+height,palette.outline);
        }
        Gui.drawRect(x,y,x+2,y+height,palette.accent);
        mc.fontRendererObj.drawStringWithShadow(labels.name,x+9,y+7,palette.name);
        if(health) {
            Gui.drawRect(x+9,y+25,x+width-9,y+31,palette.track);
            Gui.drawRect(x+9,y+25,x+9+(int)((width-18)*labels.healthFraction),y+31,
                palette.accent);
            mc.fontRendererObj.drawStringWithShadow(labels.healthLabel,
                x+width-9-labels.healthLabelWidth,y+8,palette.healthText);
        }
    }
}
