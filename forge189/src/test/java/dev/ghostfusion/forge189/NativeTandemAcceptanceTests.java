package dev.ghostfusion.forge189;

import dev.ghostfusion.features.ModuleInteractionStateMachine;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Enabled;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Frame;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Scene;
import dev.ghostfusion.features.ModuleInteractionStateMachine.InventoryOwner;
import dev.ghostfusion.features.ModuleInteractionStateMachine.SprintOwner;
import dev.ghostfusion.features.ChestStealerPolicy;
import dev.ghostfusion.features.WTapPolicy;
import dev.ghostfusion.host.ModuleActionTelemetry;
import dev.ghostfusion.host.ModuleActionTelemetry.Event;
import dev.ghostfusion.host.ModuleActionTelemetry.Lane;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.item.Item;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Combined Forge 1.8.9 Java8 native boundary acceptance: actual Minecraft
 * chest container slot mapping/quick-move, S12/S27/Netty, and the same shared
 * gameplay/gui/Panic ownership decisions on one deterministic fake session.
 *
 * This does NOT launch Minecraft, construct a real local player, join a server,
 * or demonstrate server inventory/attack acknowledgements.
 */
public final class NativeTandemAcceptanceTests {
    private static int checks;
    private static void ok(boolean pass,String reason) {
        checks++;
        if(!pass)throw new AssertionError(reason);
    }
    private static final class NativeInbox extends ChannelInboundHandlerAdapter {
        final List<Object> packets=new ArrayList<Object>();
        @Override public void channelRead(ChannelHandlerContext ctx,Object msg) {
            packets.add(msg);
        }
    }
    private static EmbeddedChannel netty(NativeInbox inbox) {
        EmbeddedChannel channel=new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        String original=channel.pipeline().context(ChannelInboundHandlerAdapter.class).name();
        channel.pipeline().addBefore(original,"packet_handler",inbox);
        return channel;
    }
    private static void receive(EmbeddedChannel ch,NativeInbox dest,Object packet) {
        int n=dest.packets.size();
        ch.writeInbound(packet);
        ok(dest.packets.size()==n+1 && dest.packets.get(n)==packet,
            "original Netty packet handler receives exactly the same packet once");
    }
    private static void chestLayoutAndTransfer(int size) {
        Item token=new Item();
        IInventory playerInventory=new InventoryBasic("native-player",false,36);
        IInventory chestInventory=new InventoryBasic("native-chest",false,size);
        ContainerChest nativeChest=new ContainerChest(playerInventory,chestInventory,null);
        ok(nativeChest.getLowerChestInventory()==chestInventory,
           "native chest lower inventory object is exact source");
        ok(nativeChest.inventorySlots.size()==size+36,
           "native chest source followed by exactly 36 player slots");
        for(int n=0;n<size;n++) {
            Slot slot=nativeChest.getSlot(n);
            ok(slot.inventory==chestInventory && slot.getSlotIndex()==n,
               "native chest shift-click mode1 source index is direct row-major slot");
        }
        for(int n=0;n<36;n++) {
            Slot slot=nativeChest.getSlot(size+n);
            int backing=n<27?n+9:n-27;
            ok(slot.inventory==playerInventory && slot.getSlotIndex()==backing,
               "native 1.8.9 chest player storage is 27 main followed by 9 hotbar");
        }
        // A real vanilla ContainerChest quick move must preserve exact item
        // quantity and clear the source, not silently duplicate a stack.
        chestInventory.setInventorySlotContents(0,new ItemStack(token,5));
        ItemStack moved=nativeChest.transferStackInSlot(null,0);
        ok(moved!=null && moved.stackSize==5 &&
           chestInventory.getStackInSlot(0)==null,
           "actual native chest shift-click source moved and cleared");
        int total=0;
        for(int i=0;i<36;i++) {
            ItemStack item=playerInventory.getStackInSlot(i);
            if(item!=null && item.getItem()==token)total+=item.stackSize;
        }
        ok(total==5,"vanilla full-stack quick-move conserved all five items");

        // Vanilla can split stacks; our conservative ChestStealer policy
        // rejects a candidate unless the *entire* stack fits. Check vanilla's
        // actual behavior separately to keep this distinction testable.
        for(int i=0;i<36;i++)
            playerInventory.setInventorySlotContents(i,
                new ItemStack(token,64));
        chestInventory.setInventorySlotContents(1,new ItemStack(token,1));
        ItemStack refused=nativeChest.transferStackInSlot(null,1);
        ok(refused==null &&
           chestInventory.getStackInSlot(1)!=null &&
           chestInventory.getStackInSlot(1).stackSize==1,
           "vanilla full inventory cannot transfer and retains chest source");
        nativeChest.onContainerClosed(null);
    }
    private static void combinedGameAndPacket() {
        final Enabled all=new Enabled(true,true,true,true,true,true,true,true);
        ModuleInteractionStateMachine arbiter=new ModuleInteractionStateMachine();
        WTapPolicy wtap=new WTapPolicy();
        ModuleActionTelemetry ledger=new ModuleActionTelemetry();
        NativeInbox inbox=new NativeInbox();
        EmbeddedChannel ch=netty(inbox);
        ForgeVelocityPacketBridge packetBridge=new ForgeVelocityPacketBridge();

        Frame f=arbiter.advance(1,0,true,Scene.GAME,true,all,false);
        ok(f.combat&&f.bridge&&f.velocity&&f.wtap&&f.autoSprint &&
            f.inventoryOwner==InventoryOwner.NONE,
           "combined combat, bridge and velocity can run on one gameplay tick");
        packetBridge.updateConnection(ch,73,50,25);
        ch.runPendingTasks();
        ok(ch.pipeline().get("ghostc_r341_velocity")!=null,
            "same session installs real Netty S12/S27 native interceptor");
        S12PacketEntityVelocity foreign=new S12PacketEntityVelocity(74,0.8,0.4,-0.2);
        receive(ch,inbox,foreign);
        ok(foreign.getMotionX()==6400,
           "world-local entity ID fence leaves foreign velocity untouched");
        S12PacketEntityVelocity self=new S12PacketEntityVelocity(73,0.8,0.4,-0.2);
        receive(ch,inbox,self);
        ok(self.getMotionX()==3200 && self.getMotionY()==800,
           "actual local 1.8.9 S12 velocity scaled while gameplay grant active");
        S27PacketExplosion blast=new S27PacketExplosion(1,2,3,2,
            Collections.<BlockPos>emptyList(),new Vec3(0.8,0.4,-0.2));
        receive(ch,inbox,blast);
        ok(Math.abs(blast.func_149149_c()-0.4f)<1e-5f &&
           Math.abs(blast.func_149144_d()-0.1f)<1e-5f,
           "native explosion knockback scales without a damage event");
        ok(wtap.attack(true,true,true,2,0,1),
           "genuine native attack-policy admission arms WTap");
        ok(ledger.record(Lane.WTAP,Event.ATTEMPT),
           "one WTap local lifecycle reservation recorded");
        f=arbiter.advance(1,1,true,Scene.GAME,true,all,wtap.pending());
        ok(f.sprintOwner==SprintOwner.WTAP && !f.autoSprint && f.velocity &&
           f.combat && f.bridge,
           "WTap sprint reservation does not block unrelated combat/packet/sneak");
        ok(wtap.tick(true,true,1,1)==WTapPolicy.Decision.RESET,
           "WTap next client tick performs sprint reset, never original attack tick");
        ok(ledger.record(Lane.WTAP,Event.DISPATCHED),
           "sprint API dispatch counted separately from remote effect");

        // Opening a real player GUI removes all gameplay permissions.
        f=arbiter.advance(1,2,true,Scene.PLAYER_INVENTORY,true,all,wtap.pending());
        ok(f.autoArmor&&!f.inventoryManager&&!f.chestStealer &&
           !f.velocity&&!f.autoSprint&&!f.combat,
           "player GUI gives AutoArmor exclusive inventory lease");
        packetBridge.reset();
        ch.runPendingTasks();
        ok(ch.pipeline().get("ghostc_r341_velocity")==null,
           "GUI transition detaches Netty velocity hook on same connection");
        wtap.reset();
        ok(ledger.record(Lane.WTAP,Event.CANCELLED)&&!ledger.pending(Lane.WTAP),
           "GUI transition cancels WTap without reporting successful restore");
        S12PacketEntityVelocity inGui=new S12PacketEntityVelocity(73,0.8,0.4,-0.2);
        receive(ch,inbox,inGui);
        ok(inGui.getMotionX()==6400,
           "packet hook removed before GUI packet dispatch");

        // Chest and player inventory modules never share a click lease.
        f=arbiter.advance(1,3,true,Scene.CHEST,true,all,false);
        ok(f.chestStealer&&!f.autoArmor&&!f.inventoryManager &&
           !f.velocity,
           "real chest scene grants only Chest Stealer to write");
        ChestStealerPolicy chest=new ChestStealerPolicy();
        boolean[] transferable=new boolean[27];
        transferable[0]=true;
        ok(chest.next(true,9,27,4,3,transferable,false,3,1)==-1,
           "native chest opens with warmup, no blind first-frame transfer");
        ok(chest.next(true,9,27,4,3,transferable,false,7,1)==0,
           "chest source 0 is scheduled only after safe opening delay");
        ok(ledger.record(Lane.CHEST,Event.ATTEMPT),
           "one admitted chest transfer attempt belongs to chest lane");
        ok(chest.next(true,9,27,4,3,transferable,false,8,1)==-1,
           "unobserved source transfer does not spawn a second click");
        ok(chest.next(true,9,27,4,3,transferable,true,9,1)==-1,
           "local source change closes pending before pacing allows next click");
        ok(ledger.record(Lane.CHEST,Event.LOCAL_CHANGE),
           "observed source mutation is local change, not server ACK");
        f=arbiter.advance(1,10,true,Scene.OTHER_GUI,true,all,false);
        ok(f.inventoryOwner==InventoryOwner.NONE && !f.combat&&!f.velocity,
           "unknown server GUIs cannot borrow game or inventory grants");
        f=arbiter.advance(1,11,true,Scene.GAME,false,all,false);
        ok(!f.combat&&!f.bridge&&!f.velocity&&!f.autoSprint,
           "focus loss revokes all gameplay writes");
        arbiter.reset();chest.reset();ledger.cancelAll();
        packetBridge.reset();
        f=arbiter.advance(2,0,false,Scene.NO_WORLD,false,all,false);
        ok(!f.autoArmor&&!f.chestStealer&&!f.wtap &&
           f.sprintOwner==SprintOwner.NONE,
           "F6/world epoch reset revokes all controllers without stale leases");
        ch.runPendingTasks();
        ok(ch.pipeline().get("ghostc_r341_velocity")==null &&
           !ledger.pending(Lane.CHEST) && !ledger.pending(Lane.WTAP),
           "Panic leaves no Netty hooks or telemetry pending actions");
        ch.finish();
    }
    public static void main(String[] args) {
        chestLayoutAndTransfer(27);
        chestLayoutAndTransfer(54);
        combinedGameAndPacket();
        ok(checks>=170,"native tandem acceptance actually checked chest and packet paths");
        System.out.println("NATIVE_TANDEM_ACCEPTANCE_PASS assertions="+checks);
    }
}
