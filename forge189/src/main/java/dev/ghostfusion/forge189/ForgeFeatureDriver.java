package dev.ghostfusion.forge189;

import dev.ghostfusion.features.CpsMeter;
import dev.ghostfusion.features.SprintPolicy;
import dev.ghostfusion.features.TargetHudPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import dev.ghostfusion.services.WorldSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
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
        if (mc.thePlayer == null || mc.theWorld == null) return;
        boolean sprint = host.featureEnabled("movement.sprint");
        if (!sprint || mc.currentScreen != null || !mc.inGameHasFocus) return;
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
    void render3d(float partialTicks) { esp.draw(partialTicks); }
    void render2d(final ScaledResolution resolution) {
        if (!host.worldPresent() || mc.thePlayer == null || mc.theWorld == null) return;
        final int sw=resolution.getScaledWidth(), sh=resolution.getScaledHeight();
        RenderStateGuard.draw(new Runnable() {
            public void run() {
                if (host.featureEnabled("hud.keystrokes")) drawKeys(sw,sh);
                if (host.featureEnabled("hud.cps")) drawCps(sw,sh);
                if (host.featureEnabled("hud.target")) drawTarget(sw,sh);
                if (host.featureEnabled("hud.diagnostics")) drawDiagnostics(sw,sh);
            }
        });
    }
    private void key(String label, int x,int y,int width,boolean pressed) {
        Gui.drawRect(x,y,x+width,y+17,pressed ? 0xCBA8A9FF : 0xB3171C30);
        int textWidth=mc.fontRendererObj.getStringWidth(label);
        mc.fontRendererObj.drawStringWithShadow(label,
            x+(width-textWidth)/2,y+5,pressed ? 0xFF121629 : 0xFFF0F0FF);
    }
    private void drawKeys(int sw,int sh) {
        int x=Math.max(3,sw-111), y=Math.max(3,sh-100);
        key("W",x+29,y,25,mc.gameSettings.keyBindForward.isKeyDown());
        key("A",x,y+20,25,mc.gameSettings.keyBindLeft.isKeyDown());
        key("S",x+29,y+20,25,mc.gameSettings.keyBindBack.isKeyDown());
        key("D",x+58,y+20,25,mc.gameSettings.keyBindRight.isKeyDown());
        int row=y+40;
        if (host.keysJump()) {
            key("SPACE",x,row,83,mc.gameSettings.keyBindJump.isKeyDown()); row+=20;
        }
        if (host.keysSneak()) key("SHIFT",x,row,83,mc.gameSettings.keyBindSneak.isKeyDown());
    }
    private void drawCps(int sw,int sh) {
        int x=Math.max(3,sw-111), y=Math.max(3,sh-24);
        int l=cps.left(System.nanoTime()), r=cps.right(System.nanoTime());
        String text=host.cpsRight() ? "L "+l+" | R "+r+" CPS" : "L "+l+" CPS";
        Gui.drawRect(x-2,y-2,x+101,y+13,0xBE131927);
        mc.fontRendererObj.drawStringWithShadow(text,x,y+1,0xFFE3E9FF);
    }
    private void drawDiagnostics(int sw,int sh) {
        int width=178, height=63;
        int x=Math.max(3,sw-width-4), y=4;
        Gui.drawRect(x,y,x+width,y+height,0xC31B2133);
        mc.fontRendererObj.drawStringWithShadow("GhostC R1.0 diagnostics",x+5,y+5,0xFFAEAAFF);
        String epoch="Epoch " + host.services().generation() + " | Tick " + host.services().tick();
        String queue="Action "+host.services().actions().pending()+
            " | Rotation "+host.services().rotations().pending();
        String applied="Aim "+host.diagnostics().count(Counter.AIM_APPLIED)+
            " | Attack "+host.diagnostics().count(Counter.EXTRA_ATTACK)+
            " | Use "+host.diagnostics().count(Counter.EXTRA_USE);
        String focus="Focus lost "+host.diagnostics().count(Counter.FOCUS_LOST)+
            " | Suppressed "+host.diagnostics().count(Counter.INPUT_SUPPRESSED);
        mc.fontRendererObj.drawStringWithShadow(epoch,x+5,y+17,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(queue,x+5,y+28,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(applied,x+5,y+39,0xFFC9D1DF);
        mc.fontRendererObj.drawStringWithShadow(focus,x+5,y+50,0xFFC9D1DF);
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
        int width=160,height=host.targetHealth()?43:29;
        int x=Math.max(2,sw/2-width/2),y=Math.max(2,sh-height-46);
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
