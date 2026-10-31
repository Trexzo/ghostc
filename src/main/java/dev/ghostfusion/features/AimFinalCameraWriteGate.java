package dev.ghostfusion.features;

import java.util.UUID;

/**
 * Last synchronous native Aim camera-write admission. Rechecks already
 * validated state after RotationCoordinator.decide(), immediately BEFORE
 * the first native rotationYaw/rotationPitch assignment.
 *
 * No Minecraft types, target selection, input capture, writes, packets,
 * persistence or clock. The ticket exists only in the owner's call stack.
 * These checks do not make concurrent native state mutations atomic; Forge
 * still invokes the two camera assignments on its normal client owner thread.
 */
public final class AimFinalCameraWriteGate {
    public static final class SourcePose {
        public final double x,y,z,eye;
        public final float yaw,pitch;
        public SourcePose(double x,double y,double z,float yaw,float pitch,double eye) {
            if(!finite(x)||!finite(y)||!finite(z)||!finite(yaw)||
               !finite(pitch)||!finite(eye)||eye<0||pitch < -90f||pitch > 90f)
                throw new IllegalArgumentException("invalid Aim source pose");
            this.x=x;this.y=y;this.z=z;
            this.yaw=yaw;this.pitch=pitch;this.eye=eye;
        }
        private boolean same(SourcePose p) {
            return p!=null&&x==p.x&&y==p.y&&z==p.z&&
                yaw==p.yaw&&pitch==p.pitch&&eye==p.eye;
        }
    }
    public static final class TargetState {
        public final double x,y,z,health;
        public final boolean dead,invisible;
        public TargetState(double x,double y,double z,double health,
                           boolean dead,boolean invisible) {
            if(!finite(x)||!finite(y)||!finite(z)||!finite(health)||health<0)
                throw new IllegalArgumentException("invalid Aim target state");
            this.x=x;this.y=y;this.z=z;this.health=health;
            this.dead=dead;this.invisible=invisible;
        }
        private boolean same(TargetState t) {
            return t!=null&&x==t.x&&y==t.y&&z==t.z&&
                health==t.health&&dead==t.dead&&invisible==t.invisible;
        }
    }
    public static final class Inputs {
        public final boolean enabled,connected,focused,screenClear;
        public final boolean requireClick,respectMouse,attackDown,useDown,usingItem;
        public final boolean mouseAvailable;
        public final int mouseX,mouseY;
        public Inputs(boolean enabled,boolean connected,boolean focused,boolean screenClear,
                      boolean requireClick,boolean respectMouse,boolean attackDown,
                      boolean useDown,boolean usingItem,boolean mouseAvailable,
                      int mouseX,int mouseY) {
            this.enabled=enabled;this.connected=connected;this.focused=focused;
            this.screenClear=screenClear;this.requireClick=requireClick;
            this.respectMouse=respectMouse;this.attackDown=attackDown;
            this.useDown=useDown;this.usingItem=usingItem;
            this.mouseAvailable=mouseAvailable;
            this.mouseX=mouseX;this.mouseY=mouseY;
        }
        private boolean same(Inputs now) {
            return now!=null&&enabled==now.enabled&&connected==now.connected&&
                focused==now.focused&&screenClear==now.screenClear&&
                requireClick==now.requireClick&&respectMouse==now.respectMouse&&
                attackDown==now.attackDown&&useDown==now.useDown&&
                usingItem==now.usingItem&&mouseAvailable==now.mouseAvailable&&
                (!respectMouse||(mouseX==now.mouseX&&mouseY==now.mouseY));
        }
        private boolean safe() {
            return enabled&&connected&&focused&&screenClear&&
                (!requireClick||attackDown)&&(!respectMouse||mouseAvailable);
        }
    }
    public static final class Ticket {
        private final long generation,tick;
        private final Object world,player,snapshot,target;
        private final int targetId;
        private final UUID targetUuid;
        private final SourcePose source;
        private final TargetState targetState;
        private final Inputs inputs;
        private Ticket(long generation,long tick,Object world,Object player,
                       Object snapshot,Object target,int targetId,UUID targetUuid,
                       SourcePose source,TargetState targetState,Inputs inputs) {
            this.generation=generation;this.tick=tick;this.world=world;
            this.player=player;this.snapshot=snapshot;this.target=target;
            this.targetId=targetId;this.targetUuid=targetUuid;
            this.source=source;this.targetState=targetState;this.inputs=inputs;
        }
    }
    private final Thread owner=Thread.currentThread();
    private static boolean finite(double value) {
        return !Double.isNaN(value)&&!Double.isInfinite(value);
    }
    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("Aim final CAMERA owner thread");
    }
    public Ticket capture(long generation,long tick,Object world,Object player,
                          Object snapshot,Object target,int targetId,UUID targetUuid,
                          SourcePose source,TargetState targetState,Inputs inputs) {
        check();
        if(generation<0||tick<0||world==null||player==null||snapshot==null||
           target==null||target==player||targetId<0||targetUuid==null||
           source==null||targetState==null||inputs==null)
            throw new IllegalArgumentException("invalid native Aim camera ticket");
        return new Ticket(generation,tick,world,player,snapshot,target,
                          targetId,targetUuid,source,targetState,inputs);
    }
    /**
     * This is a VETO, not an authorization to rotate. The caller must also
     * verify original Aim snapshot provenance, current-world entity identity,
     * target/source snapshot pose, and winning CAMERA coordinator ownership.
     */
    public boolean permits(Ticket ticket,long generation,long tick,
                           Object world,Object player,Object snapshot,
                           Object target,int targetId,UUID targetUuid,
                           SourcePose source,TargetState targetState,Inputs inputs) {
        check();
        return ticket!=null&&ticket.generation==generation&&ticket.tick==tick&&
            ticket.world==world&&ticket.player==player&&ticket.snapshot==snapshot&&
            ticket.target==target&&ticket.targetId==targetId&&
            targetUuid!=null&&ticket.targetUuid.equals(targetUuid)&&
            ticket.source.same(source)&&ticket.targetState.same(targetState)&&
            ticket.inputs.same(inputs)&&inputs.safe()&&
            !targetState.dead&&targetState.health>0;
    }
}
