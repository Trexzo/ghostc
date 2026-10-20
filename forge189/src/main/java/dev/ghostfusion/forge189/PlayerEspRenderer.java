package dev.ghostfusion.forge189;

import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import dev.ghostfusion.features.EspRenderMode;
import dev.ghostfusion.features.EspSkeletonGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

/** In-world player boxes/tracers, independent of packet and combat paths. */
final class PlayerEspRenderer {
    private final Minecraft mc;
    private final HostRuntime host;
    private static final EspSkeletonGeometry.Sink SKELETON_LINES =
        new EspSkeletonGeometry.Sink() {
            public void line(double x1,double y1,double z1,
                             double x2,double y2,double z2) {
                GL11.glVertex3d(x1,y1,z1);
                GL11.glVertex3d(x2,y2,z2);
            }
        };
    PlayerEspRenderer(Minecraft minecraft, HostRuntime host) {
        this.mc=minecraft; this.host=host;
    }
    void draw(final float partialTicks) {
        if(!host.featureEnabled("render.esp") || mc.theWorld==null ||
           mc.thePlayer==null || !Float.isFinite(partialTicks)) return;
        RenderStateGuard.draw(new Runnable() {
            public void run() { render(partialTicks); }
        });
    }
    private void render(float partialTicks) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        if(host.espThroughWalls()) GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(host.espLineWidth());
        final RenderManager rm=mc.getRenderManager();
        if (rm==null || !Double.isFinite(rm.viewerPosX) ||
            !Double.isFinite(rm.viewerPosY) || !Double.isFinite(rm.viewerPosZ)) return;
        host.diagnostics().record(Counter.ESP_RENDER_FRAME);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-rm.viewerPosX, -rm.viewerPosY, -rm.viewerPosZ);
            int count=0;
            double maximum=host.espRange();
            double limitSq=maximum*maximum;
            final EspRenderMode mode=EspRenderMode.of(host.espStyle());
            boolean boxes=mode.boxes;
            boolean tracers=mode.tracers;
            boolean skeleton=mode.skeleton;
            for(Object obj:mc.theWorld.playerEntities) {
                if(!(obj instanceof EntityPlayer)) continue;
                EntityPlayer p=(EntityPlayer)obj;
                if(p==mc.thePlayer || p.isDead || p.getHealth()<=0) continue;
                if(host.espVisible() && !mc.thePlayer.canEntityBeSeen(p)) continue;
                double distance=mc.thePlayer.getDistanceSqToEntity(p);
                if(!Double.isFinite(distance)||distance>limitSq) continue;
                double xx=p.lastTickPosX+(p.posX-p.lastTickPosX)*partialTicks;
                double yy=p.lastTickPosY+(p.posY-p.lastTickPosY)*partialTicks;
                double zz=p.lastTickPosZ+(p.posZ-p.lastTickPosZ)*partialTicks;
                if (!Double.isFinite(xx) || !Double.isFinite(yy) || !Double.isFinite(zz))
                    continue; // never submit NaN/infinite GL geometry
                AxisAlignedBB original=p.getEntityBoundingBox();
                if(original==null || !Double.isFinite(original.minX) ||
                    !Double.isFinite(original.minY) || !Double.isFinite(original.minZ) ||
                    !Double.isFinite(original.maxX) || !Double.isFinite(original.maxY) ||
                    !Double.isFinite(original.maxZ)) continue;
                AxisAlignedBB box=original.offset(xx-p.posX,yy-p.posY,zz-p.posZ).expand(0.04,0.06,0.04);
                if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) ||
                    !Double.isFinite(box.minZ) || !Double.isFinite(box.maxX) ||
                    !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)) continue;
                // No GL mode may submit invalid height/eye geometry. Skeleton
                // interpolation additionally needs BOTH yaw samples and a
                // legal frame fraction, not only a valid current body yaw.
                if(!Double.isFinite(p.height) || p.height < 0.5 || p.height > 3.0 ||
                   (skeleton && (!EspSkeletonGeometry.valid(xx,yy,zz,
                       p.renderYawOffset,p.height,p.limbSwing,p.limbSwingAmount) ||
                       !Double.isFinite(p.prevRenderYawOffset) ||
                       partialTicks < 0f || partialTicks > 1f))) {
                    host.diagnostics().record(Counter.ESP_INVALID_GEOMETRY);
                    continue;
                }
                // Limit successfully admitted players, not corrupt entries.
                if(count>=80) break;
                count++;
                GL11.glColor4f(0.62f,0.56f,1.0f,0.82f);
                if(boxes) wireBox(box);
                if(skeleton) {
                    double yaw=EspSkeletonGeometry.interpolateYaw(
                        p.prevRenderYawOffset,p.renderYawOffset,partialTicks);
                    GL11.glBegin(GL11.GL_LINES);
                    try {
                        EspSkeletonGeometry.emit(xx,yy,zz,yaw,p.height,
                            p.isSneaking(),p.limbSwing,p.limbSwingAmount,
                            SKELETON_LINES);
                    } finally { GL11.glEnd(); }
                }
                if(tracers) {
                    GL11.glBegin(GL11.GL_LINES);
                    try {
                        GL11.glVertex3d(rm.viewerPosX,rm.viewerPosY+mc.thePlayer.getEyeHeight(),rm.viewerPosZ);
                        GL11.glVertex3d(xx,yy+p.height*0.5,zz);
                    } finally { GL11.glEnd(); }
                }
                host.diagnostics().record(Counter.ESP_DRAWN_PLAYER);
                if(skeleton) host.diagnostics().record(Counter.ESP_DRAWN_SKELETON);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }
    private static void line(double x1,double y1,double z1,double x2,double y2,double z2) {
        GL11.glVertex3d(x1,y1,z1); GL11.glVertex3d(x2,y2,z2);
    }
    private static void wireBox(AxisAlignedBB a) {
        GL11.glBegin(GL11.GL_LINES);
        try {
            line(a.minX,a.minY,a.minZ,a.maxX,a.minY,a.minZ);
            line(a.maxX,a.minY,a.minZ,a.maxX,a.minY,a.maxZ);
            line(a.maxX,a.minY,a.maxZ,a.minX,a.minY,a.maxZ);
            line(a.minX,a.minY,a.maxZ,a.minX,a.minY,a.minZ);
            line(a.minX,a.maxY,a.minZ,a.maxX,a.maxY,a.minZ);
            line(a.maxX,a.maxY,a.minZ,a.maxX,a.maxY,a.maxZ);
            line(a.maxX,a.maxY,a.maxZ,a.minX,a.maxY,a.maxZ);
            line(a.minX,a.maxY,a.maxZ,a.minX,a.maxY,a.minZ);
            line(a.minX,a.minY,a.minZ,a.minX,a.maxY,a.minZ);
            line(a.maxX,a.minY,a.minZ,a.maxX,a.maxY,a.minZ);
            line(a.maxX,a.minY,a.maxZ,a.maxX,a.maxY,a.maxZ);
            line(a.minX,a.minY,a.maxZ,a.minX,a.maxY,a.maxZ);
        } finally { GL11.glEnd(); }
    }
}
