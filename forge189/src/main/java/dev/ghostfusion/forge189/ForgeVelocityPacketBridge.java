package dev.ghostfusion.forge189;

import dev.ghostfusion.features.VelocityPacketMath;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import java.lang.reflect.Field;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

/**
 * Optional inbound packet velocity scaling, isolated to the active game
 * connection. No packets are cancelled, delayed or resent. The immutable
 * configuration is published on the Minecraft owner thread; Netty consumes
 * only primitive values and packet objects belonging to its own channel.
 *
 * Reflection uses the exact MCP/SRG aliases of 1.8.9; if unavailable,
 * packets pass through unchanged rather than breaking the connection.
 */
final class ForgeVelocityPacketBridge {
    private static final String HOOK="ghostc_r341_velocity";
    private static final Field CHANNEL=field(NetworkManager.class,
        "channel","field_150746_k");
    private static final Field S12_X=field(S12PacketEntityVelocity.class,
        "motionX","field_149415_b");
    private static final Field S12_Y=field(S12PacketEntityVelocity.class,
        "motionY","field_149416_c");
    private static final Field S12_Z=field(S12PacketEntityVelocity.class,
        "motionZ","field_149414_d");
    private static final Field S27_X=field(S27PacketExplosion.class,
        "field_149152_f","motionX");
    private static final Field S27_Y=field(S27PacketExplosion.class,
        "field_149153_g","motionY");
    private static final Field S27_Z=field(S27PacketExplosion.class,
        "field_149159_h","motionZ");
    private static final Settings OFF=new Settings(false,-1,100,100);
    private volatile Settings current=OFF;
    private volatile Channel channel;

    private static Field field(Class<?> type,String... names) {
        for(String name:names) {
            try {
                Field f=type.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch(ReflectiveOperationException ignored) {
                // Try known SRG/MCP name.
            } catch(SecurityException denied) {
                return null;
            }
        }
        return null;
    }
    static final class Settings {
        final boolean enabled;
        final int entityId;
        final double horizontal,vertical;
        Settings(boolean enabled,int id,double horizontal,double vertical) {
            this.enabled=enabled;
            this.entityId=id;
            this.horizontal=horizontal;
            this.vertical=vertical;
        }
    }
    void update(NetworkManager network,int entityId,double h,double v) {
        // Called only by the actual client owner tick after world/focus checks.
        if(network==null || entityId<0 || h<0 || h>100 || v<0 || v>100 ||
           !Double.isFinite(h)||!Double.isFinite(v)) {
            reset();return;
        }
        Channel ch=null;
        if(CHANNEL!=null) {
            try {ch=(Channel)CHANNEL.get(network);}
            catch(IllegalAccessException|ClassCastException ignored) {
                reset();return;
            }
        }
        if(ch==null || !ch.isOpen()) {reset();return;}
        Channel previous=channel;
        if(previous!=ch) {
            current=OFF;
            channel=ch;
            if(previous!=null)uninstall(previous);
            install(ch);
        }
        current=new Settings(true,entityId,h,v);
    }
    void reset() {
        // OFF is visible to Netty *before* removal is scheduled.
        current=OFF;
        Channel old=channel;
        channel=null;
        if(old!=null)uninstall(old);
    }
    private void install(final Channel ch) {
        ch.eventLoop().execute(new Runnable() {
            public void run() {
                if(channel!=ch || !ch.isOpen())return;
                ChannelPipeline pipeline=ch.pipeline();
                if(pipeline.get(HOOK)!=null)return;
                if(pipeline.get("packet_handler")==null)return;
                pipeline.addBefore("packet_handler",HOOK,new Inbound(ch));
            }
        });
    }
    private void uninstall(final Channel ch) {
        ch.eventLoop().execute(new Runnable() {
            public void run() {
                if(ch.pipeline().get(HOOK)!=null)
                    ch.pipeline().remove(HOOK);
            }
        });
    }
    private final class Inbound extends ChannelDuplexHandler {
        private final Channel mine;
        Inbound(Channel ch){mine=ch;}
        @Override public void channelRead(ChannelHandlerContext ctx,Object msg)
            throws Exception {
            try {
                Settings s=current;
                if(channel==mine && ctx.channel()==mine && s.enabled &&
                   (s.horizontal!=100 || s.vertical!=100)) {
                    if(msg instanceof S12PacketEntityVelocity)
                        s12((S12PacketEntityVelocity)msg,s);
                    else if(msg instanceof S27PacketExplosion)
                        s27((S27PacketExplosion)msg,s);
                }
            } catch(ReflectiveOperationException ignored) {
                // Unknown native field mapping: do not touch the packet.
            } catch(IllegalArgumentException ignored) {
                // Unsupported vector: preserve vanilla packet values.
            } catch(RuntimeException ignored) {
                // A feature fault must never prevent native packet dispatch.
            }
            // Always allow vanilla NetHandlerPlayClient to process the packet.
            ctx.fireChannelRead(msg);
        }
    }
    private static void s12(S12PacketEntityVelocity packet,Settings s)
        throws IllegalAccessException {
        if(!VelocityPacketMath.localS12(packet.getEntityID(),s.entityId) ||
           S12_X==null||S12_Y==null||S12_Z==null)return;
        int x=packet.getMotionX(),y=packet.getMotionY(),z=packet.getMotionZ();
        int[] scaled=VelocityPacketMath.s12(x,y,z,s.horizontal,s.vertical);
        // All three fields are validated before any mutation.
        S12_X.setInt(packet,scaled[0]);
        S12_Y.setInt(packet,scaled[1]);
        S12_Z.setInt(packet,scaled[2]);
    }
    private static void s27(S27PacketExplosion packet,Settings s)
        throws IllegalAccessException {
        if(S27_X==null||S27_Y==null||S27_Z==null)return;
        float x=S27_X.getFloat(packet),y=S27_Y.getFloat(packet),
              z=S27_Z.getFloat(packet);
        float[] scaled=VelocityPacketMath.s27(x,y,z,s.horizontal,s.vertical);
        S27_X.setFloat(packet,scaled[0]);
        S27_Y.setFloat(packet,scaled[1]);
        S27_Z.setFloat(packet,scaled[2]);
    }
}
