package dev.ghostfusion.forge189;

import dev.ghostfusion.features.CpsMeter;
import dev.ghostfusion.ui.HudPositions.Widget;
import dev.ghostfusion.ui.HudFootprints;
import dev.ghostfusion.ui.HudWidgetViewport;
import dev.ghostfusion.ui.DiagnosticsHudLayout;
import dev.ghostfusion.ui.HudTextFit;
import dev.ghostfusion.ui.ModuleListLayout;
import dev.ghostfusion.ui.HudThemeTokens;
import dev.ghostfusion.features.SprintPolicy;
import dev.ghostfusion.features.TargetHudPolicy;
import dev.ghostfusion.features.HudLiveTargetGate;
import dev.ghostfusion.features.TargetHudTextLayout;
import dev.ghostfusion.features.TargetHudDistanceText;
import dev.ghostfusion.features.EspTeamPolicy;
import dev.ghostfusion.features.TargetHudPalette;
import dev.ghostfusion.features.TargetHudModernLayout;
import dev.ghostfusion.features.TargetHudMotion;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import dev.ghostfusion.services.WorldSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
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
    private final TargetHudMotion targetMotion = new TargetHudMotion();
    // Reused, client-thread-only admission for snapshot IDs. No per-frame
    // predicate allocations; no extra LOS rays or snapshot construction.
    private final java.util.function.IntPredicate nonTeammate =
        new java.util.function.IntPredicate() {
            public boolean test(int id) {
                if(mc.theWorld==null || mc.thePlayer==null)return false;
                Entity candidate=mc.theWorld.getEntityByID(id);
                return candidate instanceof EntityPlayer && candidate!=mc.thePlayer &&
                    !EspTeamPolicy.exclude(mc.thePlayer.getTeam(),
                        ((EntityPlayer)candidate).getTeam());
            }
        };
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
    void reset() { cps.clear(); targetMotion.reset(); }
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
        drawFeature("hud.modulelist", new Runnable() {
            public void run() { drawEnabledModules(sw,sh); }
        });
    }
    private void drawEnabledModules(int sw,int sh) {
        // Shared Minecraft font measurement, no new snapshot or LOS work.
        ModuleListLayout.Frame frame=ModuleListLayout.calculate(host.modules(),sw,sh,
            mc.fontRendererObj.FONT_HEIGHT,host.moduleListMaxRows(),
            host.moduleListShowRender(),host.moduleListPosition(),
            host.moduleListOrder(),targetFontWidths,host.moduleListCompact());
        if(frame.rows.isEmpty())return;
        final String appearance=host.moduleListAppearance();
        final HudThemeTokens theme=HudThemeTokens.of(appearance);
        final boolean background=host.moduleListBackground();
        final boolean stripe=host.moduleListAccent();
        // Classic remains pixel-identical, including the original UI-theme override.
        final int classicAccent=host.ui().theme()==dev.ghostfusion.ui.UiState.Theme.GRAPHITE
            ? 0xFFB9C5D6 : 0xFFA59AFF;
        final int accent="classic".equals(appearance)?classicAccent:theme.accent;
        for(ModuleListLayout.Row row:frame.rows) {
            if(background){
                Gui.drawRect(row.x,row.y,row.x+row.width,row.y+row.height,theme.panel);
                if("nebula".equals(appearance))
                    Gui.drawRect(row.x+3,row.y,row.x+row.width,row.y+1,theme.sheen);
            }
            if(stripe){
                Gui.drawRect(row.x,row.y,row.x+2,row.y+row.height,accent);
                if("nebula".equals(appearance) && row.height>=4)
                    Gui.drawRect(row.x,row.y+row.height-2,row.x+2,row.y+row.height,
                        theme.secondary);
            }
            mc.fontRendererObj.drawStringWithShadow(row.label,row.textX,row.textY,
                theme.text);
        }
    }
    private String keyName(KeyBinding binding) {
        int code = binding.getKeyCode();
        if (code == 0) return "?";
        String display = GameSettings.getKeyDisplayString(code);
        return display == null || display.trim().isEmpty() ? "?" :
            display.toUpperCase(java.util.Locale.ROOT);
    }
    private void key(String label, int x,int y,int width,boolean pressed) {
        final String appearance=host.keysAppearance();
        if("classic".equals(appearance)) {
            // Preserve legacy key panels and colors byte-for-byte by default.
            Gui.drawRect(x,y,x+width,y+17,pressed ? 0xCBA8A9FF : 0xB3171C30);
            String fitted=HudTextFit.fit(label,Math.max(0,width-5),targetFontWidths);
            if(fitted.isEmpty())return;
            int textWidth=mc.fontRendererObj.getStringWidth(fitted);
            mc.fontRendererObj.drawStringWithShadow(fitted,
                x+(width-textWidth)/2,y+5,pressed ? 0xFF121629 : 0xFFF0F0FF);
            return;
        }
        HudThemeTokens theme=HudThemeTokens.of(appearance);
        Gui.drawRect(x,y,x+width,y+17,pressed?theme.accent:theme.panel);
        if("nebula".equals(appearance)) {
            // A restrained one-pixel aurora line, never text-obscuring full glow.
            Gui.drawRect(x,y,x+width,y+1,pressed?theme.secondary:theme.frame);
            if(!pressed)Gui.drawRect(x+1,y+15,x+width-1,y+16,theme.sheen);
        }
        String fitted=HudTextFit.fit(label,Math.max(0,width-5),targetFontWidths);
        if(fitted.isEmpty())return;
        int textWidth=mc.fontRendererObj.getStringWidth(fitted);
        mc.fontRendererObj.drawStringWithShadow(fitted,
            x+(width-textWidth)/2,y+5,
            pressed?0xFF121629:theme.text);
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
        final String appearance=host.cpsAppearance();
        if("classic".equals(appearance)) {
            Gui.drawRect(x-2,y-2,right,bottom,0xBE131927);
            if(!text.isEmpty())
                mc.fontRendererObj.drawStringWithShadow(text,x,y+1,0xFFE3E9FF);
        } else {
            HudThemeTokens theme=HudThemeTokens.of(appearance);
            Gui.drawRect(x-2,y-2,right,bottom,theme.panel);
            // Keep the same bounded 103x17 card; no extra HUD footprint.
            Gui.drawRect(x-2,y-2,x-1,bottom,theme.accent);
            if("nebula".equals(appearance) && right>x)
                Gui.drawRect(x-1,y-2,right,y-1,theme.sheen);
            if(!text.isEmpty())
                mc.fontRendererObj.drawStringWithShadow(text,x,y+1,theme.text);
        }
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
    /**
     * Opt-in visual-only styles. Keeps the legacy Classic/Glass paint path
     * untouched and never modifies targeting, rotation or packet state.
     */
    private void drawModernTarget(int x,int y,int width,int height,boolean health,
                                  TargetHudTextLayout.Result labels,TargetHudPalette palette,
                                  TargetHudModernLayout style,EntityPlayer player) {
        final int fontHeight=mc.fontRendererObj.FONT_HEIGHT;
        if(!style.fits(width,height,fontHeight,health))return;
        Gui.drawRect(x,y,x+width,y+height,palette.background);
        // A one-pixel translucent frame provides definition without GL shaders.
        Gui.drawRect(x,y,x+width,y+1,palette.outline);
        Gui.drawRect(x,y+height-1,x+width,y+height,palette.outline);
        // Minimal is the only consumer of this restrained alternative painter.
        Gui.drawRect(x+1,y+1,x+3,y+height-1,palette.accent);
        Gui.drawRect(x+3,y+height-2,x+width-1,y+height-1,palette.secondary);
        mc.fontRendererObj.drawStringWithShadow(labels.name,x+9,
            y+style.nameY,palette.name);
        if(health) {
            final int barLeft=x+9,barRight=x+width-9;
            final int barTop=y+style.trackY;
            Gui.drawRect(barLeft,barTop,barRight,
                barTop+style.trackHeight,palette.track);
            int filled=(int)Math.round((barRight-barLeft)*labels.healthFraction);
            if(filled>0)
                Gui.drawRect(barLeft,barTop,barLeft+filled,
                    barTop+style.trackHeight,palette.accent);
            mc.fontRendererObj.drawStringWithShadow(labels.healthLabel,
                x+width-9-labels.healthLabelWidth,y+style.healthLabelY,palette.healthText);
        }
        if(host.targetDistance()) {
            final int distanceY=y+(health
                ?style.distanceWithHealthY:style.distanceWithoutHealthY);
            String text=TargetHudDistanceText.format(
                player.posX,player.posY,player.posZ,
                mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ,
                host.targetRange(),width-18,targetFontWidths);
            if(!text.isEmpty())
                mc.fontRendererObj.drawStringWithShadow(text,
                    x+width-9-mc.fontRendererObj.getStringWidth(text),distanceY,
                    palette.secondary);
        }
    }
    /**
     * Nebula v2: its own aurora-inspired composition, not a recolored Minimal.
     * No textures, shaders, caches or simulated targets.
     */
    private void drawNebulaTarget(int x,int y,int width,int height,boolean health,
                                  TargetHudTextLayout.Result labels,EntityPlayer player) {
        TargetHudModernLayout layout=TargetHudModernLayout.of("nebula");
        if(!layout.fits(width,height,mc.fontRendererObj.FONT_HEIGHT,health))return;
        targetMotion.update(player,System.nanoTime(),labels.healthFraction,host.targetMotion());
        double alpha=targetMotion.opacity();
        final HudThemeTokens theme=HudThemeTokens.of("nebula");
        // Deep midnight base with low-contrast inset and a contained four-sided frame.
        Gui.drawRect(x,y,x+width,y+height,TargetHudMotion.fade(theme.panel,alpha));
        int edge=TargetHudMotion.fade(theme.frame,alpha);
        Gui.drawRect(x,y,x+width,y+1,edge);
        Gui.drawRect(x,y+height-1,x+width,y+height,edge);
        Gui.drawRect(x,y,x+1,y+height,edge);
        Gui.drawRect(x+width-1,y,x+width,y+height,edge);
        Gui.drawRect(x+2,y+2,x+width-2,y+5,
            TargetHudMotion.fade(theme.sheen,alpha));
        // A short, subdued violet-to-cyan aurora. No bright full-width RGB frame.
        Gui.drawRect(x+8,y+3,x+width/2,y+4,
            TargetHudMotion.fade(theme.accent,alpha));
        Gui.drawRect(x+width/2,y+3,x+width-16,y+4,
            TargetHudMotion.fade(theme.secondary,alpha*0.65));
        Gui.drawRect(x+2,y+7,x+4,y+height-7,
            TargetHudMotion.fade(theme.accent,alpha));
        Gui.drawRect(x+width-4,y+height-10,x+width-2,y+height-4,
            TargetHudMotion.fade(theme.secondary,alpha));
        mc.fontRendererObj.drawStringWithShadow(labels.name,x+9,
            y+layout.nameY,TargetHudMotion.fade(theme.text,alpha));
        if(health) {
            int l=x+9,r=x+width-9,t=y+layout.trackY,h=layout.trackHeight;
            Gui.drawRect(l,t,r,t+h,TargetHudMotion.fade(theme.track,alpha));
            int filled=(int)Math.round((r-l)*targetMotion.health());
            if(filled>0) {
                Gui.drawRect(l,t,l+filled,t+h,TargetHudMotion.fade(theme.accent,alpha));
                if(filled>=5)
                    Gui.drawRect(l+filled-2,t,l+filled,t+h,
                        TargetHudMotion.fade(theme.secondary,alpha));
            }
            mc.fontRendererObj.drawStringWithShadow(labels.healthLabel,
                x+width-9-labels.healthLabelWidth,y+layout.healthLabelY,
                TargetHudMotion.fade(theme.muted,alpha));
        }
        if(host.targetDistance()) {
            int distY=y+(health?layout.distanceWithHealthY:layout.distanceWithoutHealthY);
            String distance=TargetHudDistanceText.format(
                player.posX,player.posY,player.posZ,
                mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ,
                host.targetRange(),width-18,targetFontWidths);
            if(!distance.isEmpty())
                mc.fontRendererObj.drawStringWithShadow(distance,
                    x+width-9-mc.fontRendererObj.getStringWidth(distance),
                    distY,TargetHudMotion.fade(theme.muted,alpha));
        }
    }
    private void drawTarget(int sw,int sh) {
        final boolean health=host.targetHealth();
        // Skip target selection/world lookup when no full card can be shown.
        HudWidgetViewport.Frame frame=HudWidgetViewport.place(
            host.ui().widgets(),Widget.TARGET,sw,sh,
            host.keysJump(),host.keysSneak(),health);
        if(frame==null)return;
        WorldSnapshot w=host.services().snapshot();
        final String selection=host.targetSelection();
        Integer crosshairId=null;
        if("crosshair".equals(selection)) {
            // The game hit result can outlive a despawn/world replacement.
            // Admit only the identical player object still in the live world.
            MovingObjectPosition hit=mc.objectMouseOver;
            if(hit!=null && hit.typeOfHit==MovingObjectPosition.MovingObjectType.ENTITY &&
               hit.entityHit instanceof EntityPlayer &&
               hit.entityHit!=mc.thePlayer) {
                EntityPlayer candidate=(EntityPlayer)hit.entityHit;
                if(mc.theWorld.getEntityByID(candidate.getEntityId())==candidate)
                    crosshairId=candidate.getEntityId();
            }
        }
        final boolean ignoreTeammates=host.targetIgnoreTeammates();
        WorldSnapshot.Entity target=ignoreTeammates
            ?targets.select(w,host.targetRange(),host.targetVisible(),
                selection,crosshairId,nonTeammate)
            :targets.select(w,host.targetRange(),host.targetVisible(),
                selection,crosshairId);
        if (target==null) { targetMotion.reset(); return; }
        Entity e=mc.theWorld.getEntityByID(target.id);
        if (!(e instanceof EntityPlayer) || e == mc.thePlayer) return;
        EntityPlayer player=(EntityPlayer)e;
        // A tick snapshot selects a stable ID, but health/death/invisibility
        // may change before this overlay callback. Never paint stale state.
        final double liveHealth=player.getHealth();
        if(!HudLiveTargetGate.eligible(player,mc.thePlayer,player.isDead,
            liveHealth,player.isInvisible(),host.targetVisible()))return;
        // Team membership can change between candidate selection and paint.
        if(ignoreTeammates && EspTeamPolicy.exclude(
            mc.thePlayer.getTeam(),player.getTeam()))return;
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
        final String skin=host.targetSkin();
        TargetHudPalette palette=TargetHudPalette.of(skin);
        if("nebula".equals(skin)) {
            drawNebulaTarget(x,y,width,height,health,labels,player);
            return;
        }
        if("minimal".equals(skin)) {
            drawModernTarget(x,y,width,height,health,labels,palette,
                TargetHudModernLayout.of(skin),player);
            return;
        }
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
        if(host.targetDistance()) {
            // Separate lower row uses existing unused card space; keep the
            // original 160px footprint and all legacy name/HP placements.
            final int distanceY=y+(health?33:18);
            if(distanceY+mc.fontRendererObj.FONT_HEIGHT<=y+height) {
                String distanceText=TargetHudDistanceText.format(
                    player.posX,player.posY,player.posZ,
                    mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ,
                    host.targetRange(),width-18,targetFontWidths);
                if(!distanceText.isEmpty())
                    mc.fontRendererObj.drawStringWithShadow(distanceText,
                        x+width-9-mc.fontRendererObj.getStringWidth(distanceText),
                        distanceY,palette.healthText);
            }
        }
    }
}
