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
    private static final Field CHANNEL=field(NetworkManager.class,Channel.class,
        "channel","field_150746_k","k");
    private static final Field S12_X=field(S12PacketEntityVelocity.class,int.class,
        "motionX","field_149415_b","b");
    private static final Field S12_Y=field(S12PacketEntityVelocity.class,int.class,
        "motionY","field_149416_c","c");
    private static final Field S12_Z=field(S12PacketEntityVelocity.class,int.class,
        "motionZ","field_149414_d","d");
    private static final Field S27_X=field(S27PacketExplosion.class,float.class,
        "field_149152_f","motionX","f");
    private static final Field S27_Y=field(S27PacketExplosion.class,float.class,
        "field_149153_g","motionY","g");
    private static final Field S27_Z=field(S27PacketExplosion.class,float.class,
        "field_149159_h","motionZ","h");
    // One atomic/volatile publication binds a *specific* channel and entity
    // identity together. Netty must never mix an old channel with a new ID.
    private static final Settings OFF=new Settings(null,false,-1,100,100);
    private volatile Settings current=OFF;

    private static Field field(Class<?> type,Class<?> expected,String... names) {
        for(String name:names) {
            try {
                Field f=type.getDeclaredField(name);
                if(f.getType()!=expected)continue;
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
        final Channel connection;
        final boolean enabled;
        final int entityId;
        final double horizontal,vertical;
        Settings(Channel connection,boolean enabled,int entityId,
                 double horizontal,double vertical) {
            this.connection=connection;
            this.enabled=enabled;
            this.entityId=entityId;
            this.horizontal=horizontal;
            this.vertical=vertical;
        }
    }
    void update(NetworkManager network,int entityId,double h,double v) {
        if(network==null || CHANNEL==null) {reset();return;}
        try {
            Channel ch=(Channel)CHANNEL.get(network);
            updateConnection(ch,entityId,h,v);
        } catch(IllegalAccessException|ClassCastException ignored) {
            reset();
        }
    }
    /** Also used by the native Java8 EmbeddedChannel contract. All
     * channel mutations still occur on the channel's own Netty event loop. */
    void updateConnection(Channel ch,int entityId,double h,double v) {
        if(ch==null || !ch.isOpen() || entityId<0 || h<0 || h>100 ||
           v<0 || v>100 || !Double.isFinite(h) || !Double.isFinite(v)) {
            reset();return;
        }
        Settings before=current;
        current=new Settings(ch,true,entityId,h,v);
        if(before.connection!=null && before.connection!=ch)
            reconcile(before.connection);
        // Reconcile *every* owner tick. If packet_handler was not yet present
        // at initial login, a later tick retries installation. Also repairs a
        // disable/re-enable race on a shared channel without duplicate hooks.
        reconcile(ch);
    }
    void reset() {
        Settings before=current;
        current=OFF; // Disable on Netty immediately, before queued removal.
        if(before.connection!=null)reconcile(before.connection);
    }
    private void reconcile(final Channel ch) {
        if(!ch.isRegistered() && !ch.isOpen())return;
        ch.eventLoop().execute(new Runnable() {
            public void run() {
                ChannelPipeline pipeline=ch.pipeline();
                Object existing=pipeline.get(HOOK);
                Settings wanted=current;
                boolean required=wanted.enabled && wanted.connection==ch &&
                    ch.isOpen();
                if(!required) {
                    // Never remove a handler owned by another module or
                    // GhostC instance using the same key.
                    if(existing instanceof Inbound &&
                       ((Inbound)existing).owner==ForgeVelocityPacketBridge.this)
                        pipeline.remove(HOOK);
                    return;
                }
                if(existing!=null)return;
                if(pipeline.get("packet_handler")==null)return;
                pipeline.addBefore("packet_handler",HOOK,new Inbound(ch));
            }
        });
    }
    private final class Inbound extends ChannelDuplexHandler {
        private final Channel mine;
        private final ForgeVelocityPacketBridge owner;
        Inbound(Channel ch){
            mine=ch;
            owner=ForgeVelocityPacketBridge.this;
        }
        @Override public void channelRead(ChannelHandlerContext ctx,Object msg)
            throws Exception {
            try {
                Settings s=current;
                if(s.connection==mine && ctx.channel()==mine && s.enabled &&
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
