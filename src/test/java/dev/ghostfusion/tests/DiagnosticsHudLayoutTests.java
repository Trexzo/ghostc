package dev.ghostfusion.tests;

import dev.ghostfusion.ui.DiagnosticsHudLayout;
import java.util.Random;

/** Bounded diagnostic panel and focused metric priority, no Minecraft needed. */
public final class DiagnosticsHudLayoutTests {
    private static int assertions;
    private static void ok(boolean yes,String why) {
        assertions++;if(!yes)throw new AssertionError(why);
    }
    private static void rejects(Runnable run,String why) {
        boolean failed=false;
        try {run.run();} catch(IllegalArgumentException expected){failed=true;}
        ok(failed,why);
    }
    private static void known() {
        DiagnosticsHudLayout full=DiagnosticsHudLayout.calculate(320,240,9);
        ok(full.x==30 && full.y==4 && full.width==286 &&
           full.height==180,"legacy panel bounds unchanged at normal resolution");
        ok(full.rows==14 && full.textWidth==276,"all original metric rows visible");
        ok(full.rowY(0)==21 && full.rowY(13)==164,
           "original row baselines preserved");
        for(int i=0;i<14;i++)
            ok(full.sourceRow(i)==i,"original row order remains "+i);
        DiagnosticsHudLayout edge=DiagnosticsHudLayout.calculate(320,180,9);
        ok(edge.rows==14 && edge.height==172 && edge.fitsViewport(320,180),
           "180px screen keeps all 14 rows inside background");
        DiagnosticsHudLayout compact=DiagnosticsHudLayout.calculate(240,120,9);
        ok(compact.rows==8 && compact.width==232 &&
           compact.fitsViewport(240,120),"small window fits eight diagnostic rows");
        for(int i=0;i<6;i++)
            ok(compact.sourceRow(i)==8+i,
               "performance and snapshot lines precede minor counters");
        DiagnosticsHudLayout tiny=DiagnosticsHudLayout.calculate(14,20,9);
        ok(tiny.rows==0,"very small panel hides instead of leaking");
        rejects(()->full.sourceRow(14),"invalid full row rejected");
        rejects(()->compact.rowY(-1),"negative compact row rejected");
        rejects(()->DiagnosticsHudLayout.calculate(0,200,9),"bad viewport rejected");
        rejects(()->DiagnosticsHudLayout.calculate(200,200,0),"bad font rejected");
        DiagnosticsHudLayout hugeFont=DiagnosticsHudLayout.calculate(300,200,
            Integer.MAX_VALUE);
        ok(hugeFont.rows==0,"huge font cannot overflow coordinate calculations");
    }
    private static void seeded() {
        Random rng=new Random(2302026L);
        for(int i=0;i<30000;i++) {
            int w=1+rng.nextInt(3840),h=1+rng.nextInt(2160);
            int font=1+rng.nextInt(28);
            DiagnosticsHudLayout panel=DiagnosticsHudLayout.calculate(w,h,font);
            if(panel.rows==0) {
                ok(panel.width==0&&panel.height==0,"hidden panel no footprint "+i);
                continue;
            }
            ok(panel.fitsViewport(w,h),"bounded canvas "+i);
            ok(panel.width<=286&&panel.height<=180&&panel.textWidth==panel.width-10,
               "bounded text and background "+i);
            ok(panel.x>=4&&panel.y==4,"viewport right-edge anchor "+i);
            boolean[] seen=new boolean[DiagnosticsHudLayout.TOTAL_ROWS];
            int lastBottom=panel.y+5+font;
            for(int slot=0;slot<panel.rows;slot++) {
                int source=panel.sourceRow(slot),y=panel.rowY(slot);
                ok(source>=0&&source<14&&!seen[source],
                   "row never duplicated or invalid "+i);
                seen[source]=true;
                ok(y>=lastBottom+2 && y+font<=panel.y+panel.height-2,
                   "all lines are non-overlapping and inside panel "+i);
                if(panel.rows==14)
                    ok(source==slot,"full panel preserves original order "+i);
                else if(slot==0)
                    ok(source==8,"compact shows FPS first "+i);
                lastBottom=y+font;
            }
        }
    }
    public static void main(String[] args) {
        known();seeded();
        ok(assertions>120000,"substantial seeded viewport regression");
        System.out.println("DIAGNOSTICS_HUD_LAYOUT_PASS assertions="+assertions);
    }
}
