package dev.ghostfusion.forge189;

import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import dev.ghostfusion.features.EspRenderMode;
import dev.ghostfusion.features.EspNearestBudget;
import dev.ghostfusion.features.EspDrawLimit;
import dev.ghostfusion.features.EspSkeletonGeometry;
import dev.ghostfusion.features.EspHealthBarGeometry;
import dev.ghostfusion.features.EspLineColor;
import dev.ghostfusion.features.EspCornerBoxGeometry;
import dev.ghostfusion.features.EspFilledBoxGeometry;
import dev.ghostfusion.features.EspDistanceFade;
import dev.ghostfusion.features.EspLivePaintGate;
import dev.ghostfusion.features.LiveCombatTargetGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

/** In-world player boxes/tracers, independent of packet and combat paths. */
final class PlayerEspRenderer {
    private final Minecraft mc;
    private final HostRuntime host;
    // Reusable, owner-thread-only candidate storage; explicitly cleared after
    // every world render so no old-world EntityPlayer references can persist.
    private final EspNearestBudget<EntityPlayer> nearest=new EspNearestBudget<EntityPlayer>(80);
    private static final EspSkeletonGeometry.Sink SKELETON_LINES =
        new EspSkeletonGeometry.Sink() {
            public void line(double x1,double y1,double z1,
                             double x2,double y2,double z2) {
                GL11.glVertex3d(x1,y1,z1);
                GL11.glVertex3d(x2,y2,z2);
            }
        };
    // GL emission is batched once for each corner box; never allocates a
    // temporary coordinate array for an individual player.
    private static final EspCornerBoxGeometry.Sink CORNER_LINES =
        new EspCornerBoxGeometry.Sink() {
            public void line(double x1,double y1,double z1,
                             double x2,double y2,double z2) {
                GL11.glVertex3d(x1,y1,z1);
                GL11.glVertex3d(x2,y2,z2);
            }
        };
    // Six faces, streamed as 24 vertices in one GL_QUADS batch, without
    // any per-player face arrays or other transient allocations.
    private static final EspFilledBoxGeometry.Sink FILL_FACES =
        new EspFilledBoxGeometry.Sink() {
            public void quad(double ax,double ay,double az,
                             double bx,double by,double bz,
                             double cx,double cy,double cz,
                             double dx,double dy,double dz) {
                GL11.glVertex3d(ax,ay,az);
                GL11.glVertex3d(bx,by,bz);
                GL11.glVertex3d(cx,cy,cz);
                GL11.glVertex3d(dx,dy,dz);
            }
        };
    private static final EspHealthBarGeometry.Sink HEALTH_LINES =
        new EspHealthBarGeometry.Sink() {
            public void segment(boolean filled,double fraction,
                                double x1,double y1,double z1,
                                double x2,double y2,double z2) {
                float hp=(float)fraction;
                if(filled) {
                    GL11.glLineWidth(3.0f);
                    GL11.glColor4f(1.0f-hp,hp,0.20f,0.96f);
                } else {
                    GL11.glLineWidth(5.0f);
                    GL11.glColor4f(0.11f,0.12f,0.18f,0.87f);
                }
                GL11.glBegin(GL11.GL_LINES);
                try {
                    GL11.glVertex3d(x1,y1,z1);
                    GL11.glVertex3d(x2,y2,z2);
                } finally { GL11.glEnd(); }
            }
        };
    PlayerEspRenderer(Minecraft minecraft, HostRuntime host) {
        this.mc=minecraft; this.host=host;
    }
    void draw(final float partialTicks) {
        if(!host.featureEnabled("render.esp") || mc.theWorld==null ||
           mc.thePlayer==null || !Float.isFinite(partialTicks) ||
           partialTicks<0f || partialTicks>1f) return;
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
            double maximum=host.espRange();
            double limitSq=maximum*maximum;
            final EspRenderMode mode=EspRenderMode.of(host.espStyle());
            boolean boxes=mode.boxes;
            boolean tracers=mode.tracers;
            boolean skeleton=mode.skeleton;
            boolean corners=mode.corners;
            boolean filled=mode.fill;
            final double cornerLength=host.espCornerLength();
            final float fillOpacity=host.espFillOpacity();
            final boolean fadeEnabled=host.espDistanceFade();
            final double fadeStart=host.espFadeStart();
            final double fadeMinimum=host.espFadeMinimum();
            final int drawBudget=host.espMaxPlayers();
            final boolean visibleOnly=host.espVisible();
            boolean health=host.espShowHealth();
            final EspHealthBarGeometry.Side healthSide=health?
                host.espHealthSide():EspHealthBarGeometry.Side.RIGHT;
            final double healthGap=health?host.espHealthGap():0.18;
            // Resolve once per rendered frame: no per-player configuration
            // reads or transient color-array allocations.
            final EspLineColor appearance=host.espLineColor();
            // Selection is bounded and stable under playerEntities ordering.
            // Validate geometry BEFORE admission so invalid players cannot
            // displace any of the nearest 80 renderable candidates.
            nearest.clear();
            // Admission should match the *configured* draw budget, not always
            // select 80 and discard 79. This advances the cutoff for LOS and
            // geometry early on crowded servers with small ESP limits.
            nearest.setLimit(drawBudget);
            try {
                for(Object obj:mc.theWorld.playerEntities) {
                    if(!(obj instanceof EntityPlayer)) continue;
                    EntityPlayer player=(EntityPlayer)obj;
                    if(player==mc.thePlayer || player.isDead) continue;
                    // Match the shared targeting snapshot: malformed health
                    // is not evidence of a living, renderable player.
                    double healthValue=player.getHealth();
                    if(!Double.isFinite(healthValue) || healthValue<=0) continue;
                    // Reject distant players before expensive line-of-sight rays.
                    // Eligible in-range players retain exactly the old gates.
                    double distance=mc.thePlayer.getDistanceSqToEntity(player);
                    if(!Double.isFinite(distance) || distance<0 || distance>limitSq) continue;
                    // Once the nearest-80 set is full, a farther candidate
                    // cannot enter it. Skip LOS and geometry for that
                    // candidate, preserving stable distance/ID tie breaks.
                    if(!nearest.couldEnter(player.getEntityId(),distance)) continue;
                    // A line-of-sight ray can be clear even for an entity with
                    // Minecraft's invisibility flag. "Visible only" requires
                    // BOTH not-invisible and a clear LOS, like target snapshots.
                    // Test invisibility before the expensive LOS ray.
                    if(visibleOnly && player.isInvisible()) continue;
                    // The active-world ID map is authoritative even if the
                    // player list still holds a stale/replaced object.
                    if(!LiveCombatTargetGate.eligible(player,
                        mc.theWorld.getEntityByID(player.getEntityId()),
                        mc.thePlayer,player.isDead,healthValue)) continue;
                    if(visibleOnly && !mc.thePlayer.canEntityBeSeen(player)) continue;
                    if(!renderable(player,partialTicks,skeleton,corners,cornerLength,filled)) continue;
                    nearest.offer(player,player.getEntityId(),distance);
                }
                final int draws=EspDrawLimit.count(drawBudget,nearest.size());
                for(int index=0;index<draws;index++) {
                    // Filled ESP still paints far-to-near, now only within
                    // the nearest *draws* subset, never the full 80 buffer.
                    final int slot=EspDrawLimit.slot(index,draws,filled);
                    EntityPlayer p=nearest.get(slot);
                    // Validate again before GL emission, never draw a stale
                    // player or a replacement sharing the previous ID.
                    if(!LiveCombatTargetGate.eligible(p,
                        mc.theWorld.getEntityByID(p.getEntityId()),mc.thePlayer,
                        p.isDead,p.getHealth(),p.isInvisible(),visibleOnly)) continue;
                    // The original nearest-distance admission is a candidate
                    // hint, not authorization to paint an old-world position.
                    final double liveDistanceSq=EspLivePaintGate.distanceSquared(
                        p.posX,p.posY,p.posZ,mc.thePlayer.posX,mc.thePlayer.posY,
                        mc.thePlayer.posZ,maximum);
                    if(!Double.isFinite(liveDistanceSq))continue;
                    // Mutable player pose/bounding boxes can be invalidated
                    // after admission by other client-side render listeners.
                    if(!renderable(p,partialTicks,skeleton,corners,cornerLength,filled))
                        continue;
                    double xx=p.lastTickPosX+(p.posX-p.lastTickPosX)*partialTicks;
                    double yy=p.lastTickPosY+(p.posY-p.lastTickPosY)*partialTicks;
                    double zz=p.lastTickPosZ+(p.posZ-p.lastTickPosZ)*partialTicks;
                    AxisAlignedBB box=p.getEntityBoundingBox()
                        .offset(xx-p.posX,yy-p.posY,zz-p.posZ).expand(0.04,0.06,0.04);
                // One finite, clamped multiplier per admitted player. Keep
                // hue constant, and do not alter semantic HP bar colors.
                // Fade reads the same live distance that passed the final
                // range fence, not the potentially stale admission distance.
                final float fade=fadeEnabled ? EspDistanceFade.factor(
                    liveDistanceSq,
                    maximum,true,fadeStart,fadeMinimum) : 1f;
                final float lineAlpha=appearance.opacity*fade;
                if(filled) {
                    // Draw the low-alpha faces first so the crisp RGB wire
                    // edges and optional skeleton remain legible on top.
                    GL11.glColor4f(appearance.red,appearance.green,
                        appearance.blue,lineAlpha*fillOpacity);
                    GL11.glBegin(GL11.GL_QUADS);
                    try {
                        EspFilledBoxGeometry.emitBackToFront(box.minX,box.minY,box.minZ,
                            box.maxX,box.maxY,box.maxZ,
                            rm.viewerPosX,rm.viewerPosY,rm.viewerPosZ,FILL_FACES);
                    } finally { GL11.glEnd(); }
                }
                GL11.glColor4f(appearance.red,appearance.green,
                    appearance.blue,lineAlpha);
                if(boxes) wireBox(box);
                if(corners) {
                    GL11.glBegin(GL11.GL_LINES);
                    try {
                        EspCornerBoxGeometry.emit(box.minX,box.minY,box.minZ,
                            box.maxX,box.maxY,box.maxZ,cornerLength,CORNER_LINES);
                    } finally { GL11.glEnd(); }
                }
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
                if(health) {
                    // Malformed health or player width must not fault an
                    // otherwise-valid ESP box, tracer or skeleton draw.
                    boolean good;
                    try {
                        good=EspHealthBarGeometry.emitStyled(xx,yy,zz,p.height,p.width,
                            rm.viewerPosX,rm.viewerPosZ,p.getHealth(),p.getMaxHealth(),
                            healthSide,healthGap,HEALTH_LINES);
                    } finally {
                        GL11.glLineWidth(host.espLineWidth());
                    }
                    if(!good) host.diagnostics().record(Counter.ESP_INVALID_GEOMETRY);
                }
                host.diagnostics().record(Counter.ESP_DRAWN_PLAYER);
                if(skeleton) host.diagnostics().record(Counter.ESP_DRAWN_SKELETON);
                }
            } finally {
                nearest.clear();
            }
        } finally {
            GL11.glPopMatrix();
        }
    }
    /** Pure read-only admission; no OpenGL state or entity list mutation. */
    private boolean renderable(EntityPlayer p,float partialTicks,
                               boolean skeleton,boolean corners,double cornerLength,
                               boolean filled) {
        double xx=p.lastTickPosX+(p.posX-p.lastTickPosX)*partialTicks;
        double yy=p.lastTickPosY+(p.posY-p.lastTickPosY)*partialTicks;
        double zz=p.lastTickPosZ+(p.posZ-p.lastTickPosZ)*partialTicks;
        if(!Double.isFinite(xx)||!Double.isFinite(yy)||!Double.isFinite(zz))
            return false;
        if(!Double.isFinite(p.posX)||!Double.isFinite(p.posY)||!Double.isFinite(p.posZ))
            return false;
        AxisAlignedBB original=p.getEntityBoundingBox();
        if(original==null || !finiteBox(original))return false;
        AxisAlignedBB box=original.offset(xx-p.posX,yy-p.posY,zz-p.posZ)
            .expand(0.04,0.06,0.04);
        if(!finiteBox(box))return false;
        if(filled && !EspFilledBoxGeometry.valid(
            box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ)) {
            host.diagnostics().record(Counter.ESP_INVALID_GEOMETRY);
            return false;
        }
        if(corners && !EspCornerBoxGeometry.valid(
            box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,cornerLength)) {
            host.diagnostics().record(Counter.ESP_INVALID_GEOMETRY);
            return false;
        }
        if(!Double.isFinite(p.height)||p.height<0.5||p.height>3.0 ||
            (skeleton && (!EspSkeletonGeometry.valid(xx,yy,zz,
                p.renderYawOffset,p.height,p.limbSwing,p.limbSwingAmount) ||
                !Double.isFinite(p.prevRenderYawOffset)))) {
            host.diagnostics().record(Counter.ESP_INVALID_GEOMETRY);
            return false;
        }
        return true;
    }
    private static boolean finiteBox(AxisAlignedBB box) {
        return Double.isFinite(box.minX)&&Double.isFinite(box.minY)&&Double.isFinite(box.minZ) &&
            Double.isFinite(box.maxX)&&Double.isFinite(box.maxY)&&Double.isFinite(box.maxZ);
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
