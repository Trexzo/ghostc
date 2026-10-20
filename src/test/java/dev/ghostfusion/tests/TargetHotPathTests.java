package dev.ghostfusion.tests;

import dev.ghostfusion.features.AimAssistPolicy;
import dev.ghostfusion.features.TargetHudPolicy;
import dev.ghostfusion.services.TargetSelector;
import dev.ghostfusion.services.WorldSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * R2.16: independent regression oracle using the retained original sorted
 * TargetSelector.select path. The hot-path single target consumers must agree
 * on target ID and camera motion without allocating/sorting candidate lists.
 */
public final class TargetHotPathTests {
    private static int checks;
    private static void ok(boolean success,String why){
        checks++;
        if(!success)throw new AssertionError(why);
    }
    private static final class Reference {
        final int id;
        final float yaw,pitch;
        final double angularError;
        Reference(int id,float yaw,float pitch,double error){
            this.id=id;this.yaw=yaw;this.pitch=pitch;this.angularError=error;
        }
    }
    private static Reference sortedAim(WorldSnapshot world,double sourceEyeHeight,
                    double targetHeight,double range,double fov,
                    double degreesPerTick,boolean visibleOnly){
        TargetSelector selector=new TargetSelector();
        WorldSnapshot.Entity best=null;
        double bestError=Double.POSITIVE_INFINITY,bestDistance=Double.POSITIVE_INFINITY;
        double bestYaw=0,bestPitch=0;
        for(WorldSnapshot.Entity e:selector.select(world,new TargetSelector.Query(
                range,visibleOnly,true,TargetSelector.Order.DISTANCE))){
            double dx=e.x-world.playerX,dz=e.z-world.playerZ;
            double horizontal=Math.hypot(dx,dz);
            if(horizontal<.0001)continue;
            double dy=e.y+targetHeight-(world.playerY+sourceEyeHeight);
            double desiredYaw=Math.toDegrees(Math.atan2(dz,dx))-90;
            double desiredPitch=-Math.toDegrees(Math.atan2(dy,horizontal));
            double deltaYaw=AimAssistPolicy.wrapDegrees(desiredYaw-world.playerYaw);
            double deltaPitch=desiredPitch-world.playerPitch;
            double error=Math.hypot(deltaYaw,deltaPitch);
            if(error>fov)continue;
            double distance=dx*dx+dy*dy+dz*dz;
            if(best==null || error<bestError-.000000001 ||
                (Math.abs(error-bestError)<=.000000001 &&
                (distance<bestDistance-.000000001 ||
                (Math.abs(distance-bestDistance)<=.000000001&&e.id<best.id)))){
                best=e;bestError=error;bestDistance=distance;
                bestYaw=deltaYaw;bestPitch=deltaPitch;
            }
        }
        if(best==null)return null;
        double factor=bestError<=degreesPerTick||bestError==0?1.0:degreesPerTick/bestError;
        return new Reference(best.id,(float)(world.playerYaw+bestYaw*factor),
            (float)Math.max(-90.0,Math.min(90.0,world.playerPitch+bestPitch*factor)),
            bestError);
    }
    private static WorldSnapshot world(List<WorldSnapshot.Entity> entities,
                                        long tick,float yaw,float pitch){
        return new WorldSnapshot(1,tick,0,0,0,yaw,pitch,entities);
    }
    private static void explicit(){
        TargetSelector targets=new TargetSelector();
        TargetHudPolicy hud=new TargetHudPolicy();
        AimAssistPolicy aim=new AimAssistPolicy();
        WorldSnapshot zero=world(Collections.<WorldSnapshot.Entity>emptyList(),1,0,0);
        ok(hud.select(zero,24,true)==null,"empty HUD");
        ok(aim.suggest(zero,1.62,1.25,6,75,4,true)==null,"empty aim");
        List<WorldSnapshot.Entity> entities=new ArrayList<WorldSnapshot.Entity>();
        entities.add(new WorldSnapshot.Entity(18,0,0,2,20,true,false,true));
        entities.add(new WorldSnapshot.Entity(12,0,0,2,20,true,true,true));
        entities.add(new WorldSnapshot.Entity(5,0,0,2,20,true,true,true));
        entities.add(new WorldSnapshot.Entity(2,0,0,1,0,true,true,true));
        entities.add(new WorldSnapshot.Entity(99,0,0,1,20,false,true,true));
        WorldSnapshot w=world(entities,2,0,0);
        ok(targets.nearest(w,6,true,true).id==5,"equal distance sorted by entity ID");
        ok(targets.nearest(w,6,false,true).id==5,"invisible equal-distance ID tie");
        ok(hud.select(w,6,true).id==5,"HUD preserves sorted selection tie");
        boolean threw=false;
        try{targets.nearest(w,0,true,true);}catch(IllegalArgumentException e){threw=true;}
        ok(threw,"nearest rejects zero range like selector query");
        threw=false;
        try{targets.nearest(null,6,true,true);}catch(IllegalArgumentException e){threw=true;}
        ok(threw,"nearest rejects null world");
    }
    private static void referenceReplay(){
        Random r=new Random(2162026L);
        AimAssistPolicy aim=new AimAssistPolicy();
        TargetHudPolicy hud=new TargetHudPolicy();
        TargetSelector selector=new TargetSelector();
        for(int frame=0;frame<1800;frame++){
            List<WorldSnapshot.Entity> candidates=new ArrayList<WorldSnapshot.Entity>();
            int n=1+r.nextInt(180);
            for(int id=0;id<n;id++){
                double x=(r.nextInt(81)-40)*.2;
                double y=(r.nextInt(17)-8)*.25;
                double z=(r.nextInt(81)-40)*.2;
                double health=r.nextInt(17)==0?0:r.nextInt(20)+1;
                boolean alive=r.nextInt(12)!=0;
                boolean visible=r.nextBoolean();
                boolean isPlayer=r.nextInt(20)!=0;
                candidates.add(new WorldSnapshot.Entity(id+1,x,y,z,health,alive,visible,isPlayer));
            }
            Collections.shuffle(candidates,r);
            float yaw=r.nextInt(361)-180,pitch=r.nextInt(171)-85;
            WorldSnapshot w=world(candidates,frame+10,yaw,pitch);
            for(int v=0;v<2;v++){
                boolean visibleOnly=v==1;
                double range=2+r.nextDouble()*22;
                List<WorldSnapshot.Entity> old=selector.select(w,new TargetSelector.Query(
                    range,visibleOnly,true,TargetSelector.Order.DISTANCE));
                WorldSnapshot.Entity nearest=selector.nearest(w,range,visibleOnly,true);
                ok((nearest==null)==old.isEmpty(),"nearest existence parity "+frame);
                if(nearest!=null){
                    ok(nearest.id==old.get(0).id,"nearest id parity "+frame);
                    ok(hud.select(w,range,visibleOnly).id==old.get(0).id,
                       "Target HUD id parity "+frame);
                }
                double aimRange=2+r.nextDouble()*4;
                double fov=5+r.nextDouble()*85;
                double speed=.5+r.nextDouble()*14.5;
                Reference expected=sortedAim(w,1.62,1.25,aimRange,fov,speed,visibleOnly);
                AimAssistPolicy.Suggestion actual=aim.suggest(
                    w,1.62,1.25,aimRange,fov,speed,visibleOnly);
                ok((expected==null)==(actual==null),"aim presence parity "+frame);
                if(actual!=null) {
                    ok(actual.entityId==expected.id,"aim target id parity "+frame);
                    ok(Math.abs(actual.yaw-expected.yaw)<.00001,
                       "aim yaw parity "+frame);
                    ok(Math.abs(actual.pitch-expected.pitch)<.00001,
                       "aim pitch parity "+frame);
                    ok(Math.abs(actual.angularError-expected.angularError)<.0000001,
                       "aim angular error parity "+frame);
                }
            }
        }
    }
    private static void stressSnapshot(){
        Random r=new Random(20261020L);
        TargetHudPolicy hud=new TargetHudPolicy();
        for(int f=0;f<250;f++){
            List<WorldSnapshot.Entity> objects=new ArrayList<WorldSnapshot.Entity>();
            for(int id=0;id<512;id++)
                objects.add(new WorldSnapshot.Entity(id,0,0,1+r.nextDouble()*23,
                    20,true,id%3==0,true));
            WorldSnapshot s=world(objects,f+1,0,0);
            ok(hud.select(s,24,true).id>=0,"512 candidate visible targeting");
            ok(hud.select(s,24,false).id>=0,"512 candidate unfiltered targeting");
        }
    }
    public static void main(String[] args){
        explicit();referenceReplay();stressSnapshot();
        ok(checks>6000,"substantial zero-sort targeting equivalence coverage");
        System.out.println("TARGET_HOT_PATH_PASS assertions="+checks);
    }
}
