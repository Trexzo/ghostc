package dev.ghostfusion.tests;

import dev.ghostfusion.features.VelocityPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.core.ModuleRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Policy event provenance, native-motion axis bounds, and live host/profile
 * registration. These are offline regressions, not in-game S12 packet tests. */
public final class VelocityTests {
    private static int checks;
    private static void ok(boolean yes,String why) {
        checks++;if(!yes)throw new AssertionError(why);
    }
    private static void rejects(Runnable r,String why) {
        boolean failed=false;
        try {r.run();} catch(RuntimeException expected){failed=true;}
        ok(failed,why);
    }
    private static VelocityPolicy.Adjustment sample(VelocityPolicy p,int hurt,
        double x,double y,double z,double horizontal,double vertical,
        long tick,long epoch) {
        return p.sample(true,true,hurt,x,y,z,horizontal,vertical,tick,epoch);
    }
    private static void nativeHurtEdges() {
        VelocityPolicy p=new VelocityPolicy();
        ok(sample(p,10,0.5,0.4,-0.5,20,50,0,1)==null,
            "enable during an existing hit does not double-apply knockback");
        ok(sample(p,9,0.4,0.3,-0.4,20,50,1,1)==null,
            "falling animation does not repeatedly scale motion");
        ok(sample(p,0,0.3,0.3,-0.3,20,50,2,1)==null,
            "zero hurt time establishes clean baseline");
        VelocityPolicy.Adjustment a=sample(p,10,0.8,0.4,-0.6,25,50,3,1);
        ok(a!=null && a.x==0.2 && a.y==0.2 && a.z==-0.15,
            "new hurt edge applies independent native horizontal/vertical scaling");
        ok(sample(p,10,0.8,0.4,-0.6,25,50,3,1)==null,
            "duplicate callback in same tick cannot apply twice");
        ok(sample(p,9,0.7,0.3,-0.5,25,50,4,1)==null,
            "remaining hurt period never applies same pulse repeatedly");
        ok(sample(p,7,0.3,0.0,-0.2,25,50,5,1)==null,
            "long falling hurt time does not double-apply");
        ok(sample(p,10,0.6,-0.25,-0.6,40,0,6,1)!=null,
            "a subsequent fresh increasing hurt value may re-arm");
        a=sample(p,9,0.1,0.0,0.1,50,100,7,1);
        ok(a==null,"post-event motion untouched");
        a=sample(p,10,0.6,-0.25,-0.6,40,0,8,1);
        ok(a!=null && a.x==0.24 && a.y==-0.25 && a.z==-0.24,
            "negative Y gravity is never reduced by vertical tuning");
        ok(sample(p,9,0.1,0.3,-0.1,100,100,9,1)==null,
            "100/100 axes preserve original motion");
        ok(sample(p,10,0.1,0.3,-0.1,100,100,10,1)==null,
            "100/100 on fresh hurt event never writes any motion");
        ok(sample(p,9,0.1,0.3,-0.1,0,0,11,1)==null,
            "changing sliders mid-hurt never generates a new impulse");
        a=sample(p,10,0.3,0.3,0.2,0,0,12,1);
        ok(a!=null && a.x==0 && a.y==0 && a.z==0,
            "0/0 new hurt intentionally nullifies positive local motion once");
        ok(sample(p,11,6.0,0.3,0.2,20,20,13,1)==null,
            "implausible motion rejected without applying more edits");
        ok(sample(p,10,0.2,0.3,0.2,30,30,0,2)==null,
            "world swap with in-progress hurt does not manufacture a hit");
        ok(sample(p,0,0.2,0.3,0.2,30,30,1,2)==null,
            "new world establishes baseline");
        ok(sample(p,10,0.5,0.5,0.5,30,30,2,2)!=null,
            "new world fresh rising hurt can be handled normally");
        ok(p.sample(false,true,10,0.5,0.4,0.4,30,30,3,2)==null,
            "disabling module clears event registration");
        ok(sample(p,10,0.5,0.5,0.5,30,30,4,2)==null,
            "reenable during ongoing damage cannot double-apply");
        ok(p.sample(true,false,10,0.5,0.4,0.4,30,30,5,2)==null,
            "unsafe native player state clears armed event");
        ok(sample(p,10,0.5,0.5,0.5,30,30,6,2)==null,
            "return from unsafe state does not reinterpret same hit");
        rejects(()->sample(p,-1,0,0,0,0,0,7,2),"negative hurt rejected");
        rejects(()->sample(p,21,0,0,0,0,0,7,2),"out-of-range hurt rejected");
        rejects(()->sample(p,10,Double.NaN,0,0,0,0,7,2),
            "NaN incoming motion rejected");
        rejects(()->sample(p,10,0,Double.POSITIVE_INFINITY,0,0,0,7,2),
            "infinite incoming motion rejected");
        rejects(()->sample(p,10,0,0,0,-0.1,0,7,2),
            "invalid horizontal fraction rejected");
        rejects(()->sample(p,10,0,0,0,0,100.1,7,2),
            "invalid vertical fraction rejected");
        rejects(()->sample(p,10,0,0,0,0,0,-1,2),
            "negative tick rejected");
        rejects(()->sample(p,10,0,0,0,0,0,7,-1),
            "negative epoch rejected");
        rejects(()->sample(p,10,0,0,0,0,0,5,2),
            "backwards session tick rejected");
        p.reset();
        ok(sample(p,10,0.4,0.4,0.4,30,30,0,3)==null,
            "Panic/focus reset fails closed to no synthetic replay");
    }
    private static void randomizedReplay() {
        for(int seed=0;seed<75;seed++) {
            Random r=new Random(3362026L+seed*307);
            VelocityPolicy p=new VelocityPolicy();
            int previous=-1;
            long last=-1;
            for(long tick=0;tick<400;tick++) {
                int hurt=r.nextInt(11);
                // The random oracle starts already in a hurt animation
                // to cover mid-hit enable (no first-frame modification).
                double x=(r.nextInt(61)-30)/50.0;
                double y=(r.nextInt(51)-25)/50.0;
                double z=(r.nextInt(61)-30)/50.0;
                double h=r.nextInt(101),v=r.nextInt(101);
                boolean rising=previous>=0 && hurt>0 && hurt>previous;
                VelocityPolicy.Adjustment a=sample(p,hurt,x,y,z,h,v,tick,1);
                boolean expected=rising && !(h==100 && v==100);
                ok((a!=null)==expected,
                    "30k deterministic single-event admission "+seed+"/"+tick);
                if(a!=null) {
                    ok(Math.abs(a.x-x*h/100.0)<1e-12 &&
                        Math.abs(a.z-z*h/100.0)<1e-12,
                        "horizontal scaling independent per seed/tick");
                    ok(Math.abs(a.y-(y>0?y*v/100.0:y))<1e-12,
                        "downward Y preserved, rising Y scaled");
                    last=tick;
                }
                previous=hurt;
            }
            ok(last>=0,"randomized stream includes a processed local hit");
        }
    }
    private static void persistenceAndOwner() throws Exception {
        VelocityPolicy p=new VelocityPolicy();
        AtomicBoolean rejected=new AtomicBoolean(false);
        Thread wrong=new Thread(()->{
            try {sample(p,10,0.2,0.2,0.2,50,50,0,1);}
            catch(IllegalStateException expected){rejected.set(true);}
        });
        wrong.start();wrong.join();
        ok(rejected.get(),"native motion module policy strictly owner-thread");
        Path dir=Files.createTempDirectory("ghostc-velocity-r336-");
        HostRuntime host=new HostRuntime(dir);
        final String id="combat.velocity";
        ok(host.modules().get(id)!=null &&
           host.modules().get(id).state()==ModuleRegistry.State.DISABLED,
           "true module registered and disabled by default");
        ok(host.velocityHorizontalPercent()==100 &&
           host.velocityVerticalPercent()==100,
           "100/100 profile is exact original Minecraft movement");
        host.worldPresence(true);
        host.uiActions().fraction(id,"horizontal_percent",0.60);
        host.uiActions().fraction(id,"vertical_percent",0.35);
        double x=host.velocityHorizontalPercent(),y=host.velocityVerticalPercent();
        ok(x==60 && y==35,"real F7 sliders expose independent velocity axes");
        host.uiActions().toggle(id);
        ok(host.featureEnabled(id),"real Forge Velocity module can be enabled");
        host.close();
        HostRuntime restored=new HostRuntime(dir);
        restored.worldPresence(true);
        ok(restored.featureEnabled(id) &&
            restored.velocityHorizontalPercent()==x &&
            restored.velocityVerticalPercent()==y,
            "typed profile persists Velocity enabled state and both axes");
        restored.uiActions().toggle(id);
        restored.close();
        HostRuntime disabled=new HostRuntime(dir);
        disabled.worldPresence(true);
        ok(!disabled.featureEnabled(id),"explicit disable persists");
        disabled.close();
    }
    public static void main(String[] args) throws Exception {
        nativeHurtEdges();
        randomizedReplay();
        persistenceAndOwner();
        ok(checks>=30000,"30k native-event replay plus UI and lifecycle");
        System.out.println("VELOCITY_PASS assertions="+checks);
    }
}
