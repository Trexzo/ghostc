package dev.ghostfusion.tests;

import dev.ghostfusion.features.TargetHudTextLayout;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.ToIntFunction;

/** Deterministic variable-font, safe labels and persisted Target HUD mode. */
public final class TargetHudTextLayoutTests {
    private static int checks;
    private static void ok(boolean yes,String why){
        checks++;if(!yes)throw new AssertionError(why);
    }
    private static final ToIntFunction<String> FONT=new ToIntFunction<String>(){
        public int applyAsInt(String text){
            int total=0;
            for(int pos=0;pos<text.length();){
                int codepoint=text.codePointAt(pos);
                pos+=Character.charCount(codepoint);
                total+=codepoint=='W'?10:codepoint=='i'?2:
                    codepoint=='.'?3:codepoint>0xffff?10:6;
            }
            return total;
        }
    };
    private static TargetHudTextLayout.Result layout(String text,
                  boolean health,String mode,double current,double maximum){
        TargetHudTextLayout.Result r=TargetHudTextLayout.layout(
            text,42,health,mode,current,maximum,160,FONT);
        ok(r.nameWidth==FONT.applyAsInt(r.name),"actual pixel length of name");
        ok(r.healthLabelWidth==FONT.applyAsInt(r.healthLabel),
           "actual pixel length of numeric HP");
        ok(r.nameWidth+(health?r.healthLabelWidth+6:0)<=142,
           "name and HP label never overlap inside 160px HUD");
        ok(r.healthFraction>=0&&r.healthFraction<=1&&
           Double.isFinite(r.healthFraction),"HP bar fraction safe");
        return r;
    }
    private static void edges(){
        TargetHudTextLayout.Result full=layout("Steve",true,"values",20,20);
        ok("Steve".equals(full.name),"legacy short player name unchanged");
        ok("20.0 / 20.0".equals(full.healthLabel),
           "legacy numeric text unchanged");
        ok(full.healthFraction==1,"full-health bar full");
        TargetHudTextLayout.Result percent=layout("Steve",true,"percent",10,40);
        ok("25%".equals(percent.healthLabel),"optional percentage text");
        ok(percent.healthFraction==.25,"health bar and text agree");
        TargetHudTextLayout.Result hidden=layout("MinecraftPlayer",
            false,"values",1,20);
        ok("MinecraftPlayer".equals(hidden.name),"unreserved full-width name");
        ok(hidden.healthLabel.length()==0,"hidden HP label empty");
        TargetHudTextLayout.Result cramped=layout(
            "WWWWWWWWWWWWWWWWWWWWWW",true,"values",1e11,1e11);
        ok(cramped.name.endsWith("..."),"long name pixel-truncated");
        ok("100%".equals(cramped.healthLabel),
           "unbounded HP values fall back to compact percentage");
        TargetHudTextLayout.Result formatted=layout("\u00a7cBad\u00a7lW\nName",
            true,"percent",2,8);
        ok(!formatted.name.contains("\u00a7")&&!formatted.name.contains("\n"),
           "Minecraft text formatting and control characters stripped");
        TargetHudTextLayout.Result fallback=layout(null,true,"values",10,20);
        ok(fallback.name.startsWith("Player #"),"null player name fallback");
        TargetHudTextLayout.Result nan=layout("Test",true,"percent",
            Double.NaN,20);
        ok("?".equals(nan.healthLabel)&&nan.healthFraction==0,
           "malformed HP reports unknown and cannot create NaN bar");
        TargetHudTextLayout.Result emoji=layout("WWWW\ud83d\ude80WWWWWW",
            true,"percent",1,20);
        ok(!emoji.name.endsWith("\ud83d")&&!emoji.name.endsWith("\ude80"),
           "font clipping does not split UTF-16 surrogate pairs");
        boolean rejected=false;
        try{TargetHudTextLayout.layout("Hi",42,true,"bad",1,2,160,FONT);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"unknown mode fails closed");
        rejected=false;
        try{TargetHudTextLayout.layout("Hi",42,true,"values",1,2,0,FONT);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"unusable width rejected");
    }
    private static void seeded(){
        Random r=new Random(2282026L);
        String letters="WimabcZd.gJX";
        for(int i=0;i<14000;i++){
            StringBuilder name=new StringBuilder();
            for(int j=r.nextInt(80);j>0;j--)
                name.append(letters.charAt(r.nextInt(letters.length())));
            if(i%49==0)name.append("\u00a7a");
            if(i%113==0)name.append("\ud83d\ude80");
            boolean health=i%4!=0;
            String mode=i%2==0?"values":"percent";
            double max=i%23==0?1e10:1+r.nextInt(200);
            double cur=r.nextDouble()*max*1.3;
            TargetHudTextLayout.Result result=layout(name.toString(),
                health,mode,cur,max);
            if(health && "percent".equals(mode))
                ok(result.healthLabel.endsWith("%"),"percent readout stays compact");
            ok(!result.name.contains("\u00a7"),"untrusted format stripped");
            ok(result.nameWidth<=142,"HUD bounds preserved");
        }
    }
    private static void profile()throws Exception{
        Path dir=Files.createTempDirectory("ghostc-r228-hud");
        HostRuntime host=new HostRuntime(dir);
        ok("values".equals(host.targetHealthText()),"legacy values default");
        host.uiActions().cycle("hud.target","health_text");
        ok("percent".equals(host.targetHealthText()),"percentage GUI option");
        host.persist();host.close();
        HostRuntime restored=new HostRuntime(dir);
        ok("percent".equals(restored.targetHealthText()),
           "setting survives legacy serialized profile");
        restored.close();
    }
    public static void main(String[] args)throws Exception{
        edges();seeded();profile();
        ok(checks>70000,"substantial deterministic font/health testing");
        System.out.println("TARGET_HUD_TEXT_PASS assertions="+checks);
    }
}
