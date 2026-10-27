package dev.ghostfusion.forge189;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

/**
 * Real Java 8 Forge packet classes + a real Netty pipeline. Does not launch
 * Minecraft or a server, but exercises native packet mutation, pass-through,
 * deferred attach, lifecycle races and channel reentry (not just pure math).
 */
public final class VelocityPacketNativeTests {
    private static int assertions;
    private static void ok(boolean yes,String why) {
        assertions++;
        if(!yes)throw new AssertionError(why);
    }
    private static final class Collector extends ChannelInboundHandlerAdapter {
        final List<Object> received=new ArrayList<Object>();
        @Override public void channelRead(ChannelHandlerContext ctx,Object msg) {
            received.add(msg);
        }
    }
    private static EmbeddedChannel channel(Collector collector,boolean handler) {
        EmbeddedChannel ch=new EmbeddedChannel();
        if(handler)ch.pipeline().addLast("packet_handler",collector);
        return ch;
    }
    private static void tick(EmbeddedChannel ch) {
        ch.runPendingTasks();
    }
    private static S12PacketEntityVelocity velocity(int id) {
        return new S12PacketEntityVelocity(id,0.8,0.4,-0.2);
    }
    private static S27PacketExplosion explosion() {
        return new S27PacketExplosion(1,2,3,2.0f,
            Collections.<BlockPos>emptyList(),new Vec3(0.8,0.4,-0.2));
    }
    private static void emits(EmbeddedChannel ch,Collector collector,Object msg,
                              String why) {
        int previous=collector.received.size();
        ch.writeInbound(msg);
        ok(collector.received.size()==previous+1 &&
           collector.received.get(previous)==msg,
           why+" / vanilla received exact packet object once");
    }
    private static void packets() {
        Collector capture=new Collector();
        EmbeddedChannel ch=channel(capture,true);
        ForgeVelocityPacketBridge bridge=new ForgeVelocityPacketBridge();
        bridge.updateConnection(ch,27,50,25);tick(ch);
        ok(ch.pipeline().get("ghostc_r341_velocity")!=null,
           "native inbound handler installed ahead of packet_handler");
        S12PacketEntityVelocity other=velocity(44);
        int raw=other.getMotionX();
        emits(ch,capture,other,"S12 for foreign entity");
        ok(other.getMotionX()==raw,"foreign entity velocity never modified");
        S12PacketEntityVelocity local=velocity(27);
        emits(ch,capture,local,"S12 local player");
        ok(local.getMotionX()==3200 && local.getMotionY()==800 &&
           local.getMotionZ()==-800,
           "actual Minecraft 1.8.9 S12 packet fields are scaled");
        S27PacketExplosion blast=explosion();
        emits(ch,capture,blast,"S27 local explosion");
        ok(Math.abs(blast.func_149149_c()-0.4f)<1e-5f &&
           Math.abs(blast.func_149144_d()-0.1f)<1e-5f &&
           Math.abs(blast.func_149147_e()+0.1f)<1e-5f,
           "actual S27 private fields scale and vanilla getters observe it");
        ok(blast.getX()==1 && blast.getY()==2 && blast.getZ()==3 &&
           blast.getStrength()==2.0f &&
           blast.getAffectedBlockPositions().isEmpty(),
           "explosion location/strength/blocks remain untouched");
        bridge.updateConnection(ch,27,100,100);tick(ch);
        S12PacketEntityVelocity full=velocity(27);
        int bx=full.getMotionX(),by=full.getMotionY(),bz=full.getMotionZ();
        emits(ch,capture,full,"native 100/100 S12");
        ok(full.getMotionX()==bx && full.getMotionY()==by &&
           full.getMotionZ()==bz,
           "native packet byte values unchanged at 100/100");
        bridge.reset();tick(ch);
        ok(ch.pipeline().get("ghostc_r341_velocity")==null,
           "disable removes only own handler");
        S27PacketExplosion untouched=explosion();
        emits(ch,capture,untouched,"disabled S27");
        ok(Math.abs(untouched.func_149149_c()-0.8f)<1e-5f,
           "disabled packet path leaves explosion untouched");
        ch.finish();
    }
    private static void lifecycleRaces() {
        Collector capture=new Collector();
        EmbeddedChannel ch=channel(capture,true);
        ForgeVelocityPacketBridge bridge=new ForgeVelocityPacketBridge();
        bridge.updateConnection(ch,9,40,40);
        bridge.reset();
        bridge.updateConnection(ch,9,25,25);
        tick(ch);
        ok(ch.pipeline().get("ghostc_r341_velocity")!=null,
           "reset+re-enable before event loop reconciliation keeps hook");
        S12PacketEntityVelocity local=velocity(9);
        emits(ch,capture,local,"rapid reenable packet");
        ok(local.getMotionX()==1600,
           "rapid reenable uses latest configured percentages");
        bridge.reset();
        bridge.updateConnection(ch,9,60,50);
        tick(ch);
        ok(ch.pipeline().get("ghostc_r341_velocity")!=null,
           "re-enable with hook already installed cannot uninstall it");
        local=velocity(9);
        emits(ch,capture,local,"already-installed reenable");
        ok(local.getMotionX()==3840,
           "fresh configured snapshot wins stale queued removal");
        bridge.updateConnection(ch,11,50,50);tick(ch);
        local=velocity(9);
        emits(ch,capture,local,"same-channel player identity switched");
        ok(local.getMotionX()==6400,
           "old local player id no longer authorizes S12 edits");
        bridge.reset();tick(ch);
        ok(ch.pipeline().get("ghostc_r341_velocity")==null,
           "Panic-style reset detaches all velocity hooks");
        ch.finish();
    }
    private static void delayedHandlerAndReconnect() {
        Collector first=new Collector();
        EmbeddedChannel old=channel(first,false);
        ForgeVelocityPacketBridge bridge=new ForgeVelocityPacketBridge();
        bridge.updateConnection(old,7,50,50);tick(old);
        ok(old.pipeline().get("ghostc_r341_velocity")==null,
           "missing vanilla packet_handler fails closed without exception");
        old.pipeline().addLast("packet_handler",first);
        bridge.updateConnection(old,7,50,50);tick(old);
        ok(old.pipeline().get("ghostc_r341_velocity")!=null,
           "later owner tick retries late vanilla handler attachment");
        Collector second=new Collector();
        EmbeddedChannel newer=channel(second,true);
        bridge.updateConnection(newer,8,50,50);
        tick(old);tick(newer);
        ok(old.pipeline().get("ghostc_r341_velocity")==null &&
           newer.pipeline().get("ghostc_r341_velocity")!=null,
           "world/connection transition detaches only old Netty channel");
        S12PacketEntityVelocity stale=velocity(7);
        emits(old,first,stale,"old disconnected channel");
        ok(stale.getMotionX()==6400,"old connection receives no ghost edits");
        S12PacketEntityVelocity foreign=velocity(7);
        emits(newer,second,foreign,"new connection foreign player");
        ok(foreign.getMotionX()==6400,"old player ID cannot authorize new");
        S12PacketEntityVelocity active=velocity(8);
        emits(newer,second,active,"new connection local player");
        ok(active.getMotionX()==3200,"only current channel/current entity scales");
        bridge.reset();tick(newer);
        ok(newer.pipeline().get("ghostc_r341_velocity")==null,
            "shutdown leaves zero GhostC handlers");
        old.finish();newer.finish();
    }
    public static void main(String[] args) {
        packets();
        lifecycleRaces();
        delayedHandlerAndReconnect();
        ok(assertions>30,"native pipeline and Minecraft packet assertions executed");
        System.out.println("VELOCITY_NATIVE_NETTY_PASS assertions="+assertions);
    }
}
