package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSlateCompactModuleScroll;
import dev.ghostfusion.ui.GuiHitZones;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.GuiChrome;
import dev.ghostfusion.ui.GuiScrollIndicator;
import dev.ghostfusion.ui.UiState;
import java.util.Random;

/** 50k realistic viewport and wheel-scroll visibility/containment tests. */
public final class GuiSlateCompactModuleScrollTests {
    private static long checks;
    private static void ok(boolean yes,String note) {
        checks++;if(!yes)throw new AssertionError(note);
    }
    private static void reject(Runnable r,String note) {
        boolean rejected=false;
        try {r.run();}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,note);
    }
    public static void main(String[] args) {
        GuiLayout common=GuiLayout.calculate(480,270,
            UiState.Layout.WIDE,UiState.Theme.SLATE);
        ok(GuiChrome.interactive(common) && common.visibleRows==2,
            "Auto Minecraft GUI scale exposes only two module rows");
        ok(GuiSlateCompactModuleScroll.of(common,2,0)==null,
            "two modules need no scroll feedback");
        GuiSlateCompactModuleScroll.Frame first=
            GuiSlateCompactModuleScroll.of(common,15,0);
        GuiSlateCompactModuleScroll.Frame last=
            GuiSlateCompactModuleScroll.of(common,15,13);
        ok(first!=null && last!=null,"overflow indicator appears");
        ok(first.x==common.x+common.width-8 &&
           first.x>=common.x+common.width-11,
            "read-only rail stays outside native module/toggle hit area");
        ok(first.y==common.listY && first.height==common.visibleRows*28 &&
           last.thumbY+last.thumbHeight==last.y+last.height,
            "thumb spans first to final visible module positions");
        ok(!GuiHitZones.moduleArea(common,first.x,first.y),
            "scrollbar never masquerades as an actionable toggle");
        ok(GuiSlateCompactModuleScroll.of(common,15,999).thumbY==last.thumbY,
            "stale filtered list scroll clamps to final real position");
        reject(()->GuiSlateCompactModuleScroll.of(null,2,0),"layout required");
        reject(()->GuiSlateCompactModuleScroll.of(common,-1,0),"negative count rejected");
        reject(()->GuiSlateCompactModuleScroll.of(common,1,-1),"negative scroll rejected");

        Random random=new Random(20261101L);
        for(int i=0;i<50000;i++) {
            int sw=1+random.nextInt(2400),sh=1+random.nextInt(1500);
            UiState.Layout mode=random.nextBoolean()?
                UiState.Layout.WIDE:UiState.Layout.COMPACT;
            GuiLayout g=GuiLayout.calculate(sw,sh,mode,UiState.Theme.SLATE);
            int total=random.nextInt(200);
            int firstIndex=random.nextInt(240);
            GuiSlateCompactModuleScroll.Frame bar=
                GuiSlateCompactModuleScroll.of(g,total,firstIndex);
            if(!GuiChrome.interactive(g)||g.visibleRows<1||
               total<=g.visibleRows) {
                ok(bar==null,"hidden when disabled or all rows fit "+i);
                continue;
            }
            ok(bar!=null,"overflow is clearly visible "+i);
            ok(bar.width==2 && bar.x>=g.x+g.width-11 &&
               bar.x+bar.width<=g.x+g.width,
               "rail is a quiet margin-only element "+i);
            ok(!GuiHitZones.moduleArea(g,bar.x,bar.y),
               "module/toggle click area remains unchanged "+i);
            ok(bar.y==g.listY && bar.height==g.visibleRows*28 &&
               bar.y+bar.height<=g.listY+g.listHeight,
               "track matches only painted visible rows "+i);
            ok(bar.thumbHeight>=1 && bar.thumbY>=bar.y &&
               bar.thumbY+bar.thumbHeight<=bar.y+bar.height,
               "thumb always fits track "+i);
            int bounded=Math.min(firstIndex,Math.max(0,total-g.visibleRows));
            ok(bar.thumbHeight==GuiScrollIndicator.height(
                bar.height,total,g.visibleRows) &&
               bar.thumbY-bar.y==GuiScrollIndicator.offset(
                bar.height,total,g.visibleRows,bounded),
                "scroll indicator shares actual wide-Slate proportional model "+i);
        }
        System.out.println("GUI_SLATE_COMPACT_MODULE_SCROLL_PASS checks="+checks);
    }
}
