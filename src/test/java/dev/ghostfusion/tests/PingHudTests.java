package dev.ghostfusion.tests;

import dev.ghostfusion.features.PingHudText;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.ui.StatusHudLayout;
import dev.ghostfusion.ui.UiState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** R2.69: ping formatting, measured metrics layout and persistence. */
public final class PingHudTests {
    private static long assertions;
    private static void ok(boolean value,String label){
        assertions++;if(!value)throw new AssertionError(label);
    }
    private static void values(){
        ok(PingHudText.format(-1).isEmpty(),"missing ping never faked");
        ok(PingHudText.format(Integer.MIN_VALUE).isEmpty(),"invalid ping suppressed");
        ok("PING 0 ms".equals(PingHudText.format(0)),"zero valid");
        ok("PING 42 ms".equals(PingHudText.format(42)),"normal latency");
        ok("PING 9999 ms".equals(PingHudText.format(9999)),"maximum exact");
        ok("PING 9999+ ms".equals(PingHudText.format(10000)),"capped");
        ok("PING 9999+ ms".equals(PingHudText.format(Integer.MAX_VALUE)),"bounded");
        Random random=new Random(2692026L);
        for(int i=0;i<120000;i++){
            int n=random.nextInt();
            String label=PingHudText.format(n);
            ok(label.length()<=13,"text width bounded "+i);
            ok(n>=0 || label.isEmpty(),"invalid hidden "+i);
            if(n>=0)ok(label.startsWith("PING "),"label prefix "+i);
        }
    }
    private static void geometry(){
        UiState ui=new UiState();ui.position(1,1);
        StatusHudLayout original=StatusHudLayout.calculate(
            ui,320,180,true,true,true,60,18,45,150,9);
        StatusHudLayout noPing=StatusHudLayout.calculate(
            ui,320,180,true,true,true,false,60,18,45,150,0,9);
        ok(original.measuredWidth==noPing.measuredWidth &&
           original.measuredHeight==noPing.measuredHeight &&
           original.x==noPing.x && original.y==noPing.y &&
           noPing.pingY==-1,"disabled ping preserves legacy pixels");
        StatusHudLayout all=StatusHudLayout.calculate(
            ui,320,180,true,true,true,true,60,18,45,150,78,9);
        ok(all.measuredHeight==original.measuredHeight+11 &&
           all.coordinatesY==29 && all.pingY==40,
           "ping row follows FPS and coordinates without overlap");
        ok(all.x==original.x && all.y==original.y-11,
           "anchored block moves up when a row is added");
        StatusHudLayout pingOnly=StatusHudLayout.calculate(
            ui,320,180,false,false,false,true,60,18,45,150,80,9);
        ok(pingOnly.measuredWidth==82 && pingOnly.measuredHeight==11 &&
           pingOnly.pingY==0 && pingOnly.fpsY==-1 &&
           pingOnly.coordinatesY==-1,"standalone metric row");
        Random random=new Random(2691226L);
        for(int i=0;i<85000;i++){
            int w=1+random.nextInt(1920),h=1+random.nextInt(1080);
            int font=1+random.nextInt(25);
            boolean status=random.nextBoolean(),fps=random.nextBoolean(),
                coords=random.nextBoolean(),ping=random.nextBoolean();
            if(!(status||fps||coords||ping))ping=true;
            int sw=1+random.nextInt(480),sh=1+random.nextInt(45);
            int fw=random.nextInt(400),cw=random.nextInt(600),pw=random.nextInt(250);
            ui.position(random.nextDouble(),random.nextDouble());
            StatusHudLayout l=StatusHudLayout.calculate(ui,w,h,
                status,fps,coords,ping,sw,sh,fw,cw,pw,font);
            int expectedHeight=(status?sh:0)+(font+2)*
                ((fps?1:0)+(coords?1:0)+(ping?1:0));
            long expectedWidth=Math.max(status?sw:0,Math.max(
                fps?(long)fw+2:0,Math.max(coords?(long)cw+2:0,
                    ping?(long)pw+2:0)));
            ok(l.measuredHeight==expectedHeight &&
               l.measuredWidth==expectedWidth,"full row measurement "+i);
            ok(l.fitsViewport(w,h),"clamped visible footprint "+i);
            ok(l.pingY==(ping?expectedHeight-font-2:-1),
                "ping last row "+i);
            ok(l.x==ui.pixelX(w,l.width)&&l.y==ui.pixelY(h,l.height),
                "consistent normalized anchor "+i);
        }
    }
    private static void persistence()throws Exception{
        Path directory=Files.createTempDirectory("ghostc-r269-ping");
        HostRuntime host=new HostRuntime(directory);
        ok(!host.showPing(),"legacy default off");
        host.uiActions().bool("hud.metrics","show_ping");
        ok(host.showPing(),"option can be enabled");
        host.persist();host.close();
        HostRuntime restored=new HostRuntime(directory);
        ok(restored.showPing(),"ping enabled across restart");
        ok(restored.uiActions().reset("hud.metrics","show_ping"),
            "single-setting reset restores default");
        ok(!restored.showPing(),"restored disabled");
        restored.close();
    }
    public static void main(String[] args)throws Exception{
        values();geometry();persistence();
        ok(assertions>400000,"substantial independent ping and HUD verification");
        System.out.println("PING_HUD_PASS assertions="+assertions);
    }
}
