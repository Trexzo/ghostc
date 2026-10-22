package dev.ghostfusion.tests;

import dev.ghostfusion.features.TargetHudDistanceText;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.ToIntFunction;

/** R2.62: optional Target HUD distance fits the existing card without overlap. */
public final class TargetHudDistanceTextTests {
    private static int checks;
    private static void ok(boolean pass,String label){
        checks++;if(!pass)throw new AssertionError(label);
    }
    private static final ToIntFunction<String> FONT=new ToIntFunction<String>() {
        public int applyAsInt(String text) {
            int n=0;
            for(int i=0;i<text.length();i++)n+=text.charAt(i)=='i'?2:6;
            return n;
        }
    };
    private static String format(double tx,double ty,double tz,
                                 double lx,double ly,double lz,int budget){
        String value=TargetHudDistanceText.format(tx,ty,tz,lx,ly,lz,24,budget,FONT);
        ok(FONT.applyAsInt(value)<=budget,"always pixel-bounded "+budget);
        return value;
    }
    private static void edges(){
        ok("5.0 blocks".equals(format(3,4,0,0,0,0,142)),
           "Pythagorean distance in Minecraft blocks");
        ok("0.0 blocks".equals(format(0,0,0,0,0,0,142)),
           "overlapping current positions");
        ok("24.0 blocks".equals(format(24,0,0,0,0,0,142)),
           "maximum Target HUD range inclusive");
        ok("".equals(format(24.00001,0,0,0,0,0,142)),
           "outside configured range suppressed");
        String abbreviated=format(3,4,0,0,0,0,24);
        ok("5.0b".equals(abbreviated),"narrow card uses compact readout");
        ok("".equals(format(3,4,0,0,0,0,15)),
           "impossibly narrow label suppressed rather than clipped");
        ok("".equals(format(Double.NaN,0,0,0,0,0,142)),
           "NaN target position rejected");
        ok("".equals(format(0,0,0,Double.POSITIVE_INFINITY,0,0,142)),
           "nonfinite local position rejected");
        ok("".equals(format(Double.MAX_VALUE,0,0,0,0,0,142)),
           "overflow squared distance suppressed");
        ok("".equals(TargetHudDistanceText.format(3,4,0,0,0,0,
            Double.NaN,142,FONT)),"invalid range never painted");
        boolean rejected=false;
        try{TargetHudDistanceText.format(3,4,0,0,0,0,24,-1,FONT);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"negative text budget rejected");
        rejected=false;
        try{TargetHudDistanceText.format(3,4,0,0,0,0,24,142,null);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"missing font rejected");
        rejected=false;
        try{TargetHudDistanceText.format(3,4,0,0,0,0,24,142,text->-1);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"negative font measurements rejected");
    }
    private static void seeded(){
        Random rnd=new Random(2622026L);
        for(int i=0;i<100000;i++) {
            double lx=rnd.nextDouble()*50000-25000;
            double ly=rnd.nextDouble()*50000-25000;
            double lz=rnd.nextDouble()*50000-25000;
            double dx=rnd.nextDouble()*60-30;
            double dy=rnd.nextDouble()*60-30;
            double dz=rnd.nextDouble()*60-30;
            int budget=rnd.nextInt(145);
            String result=format(lx+dx,ly+dy,lz+dz,lx,ly,lz,budget);
            double sq=dx*dx+dy*dy+dz*dz;
            if(sq>24*24)ok(result.isEmpty(),
                "out-of-range target never displays distance "+i);
            if(!result.isEmpty()){
                ok(result.endsWith("b")||result.endsWith(" blocks"),
                    "only two bounded label forms");
                ok(!result.contains(",") && result.length()<=11,
                    "locale-stable compact label");
            }
        }
    }
    private static void persistence()throws Exception{
        Path root=Files.createTempDirectory("ghostc-r262-distance");
        HostRuntime host=new HostRuntime(root);
        ok(!host.targetDistance(),"legacy default hides new distance line");
        host.uiActions().bool("hud.target","show_distance");
        ok(host.targetDistance(),"ClickGUI Bool enables distance label");
        host.persist();
        host.close();
        HostRuntime loaded=new HostRuntime(root);
        ok(loaded.targetDistance(),"distance option survives profile reload");
        loaded.uiActions().bool("hud.target","show_distance");
        ok(!loaded.targetDistance(),"distance option can be toggled off");
        loaded.close();
    }
    public static void main(String[] args)throws Exception{
        edges();seeded();persistence();
        ok(checks>100000,"extensive independent distance formatter replay");
        System.out.println("TARGET_HUD_DISTANCE_PASS assertions="+checks);
    }
}
