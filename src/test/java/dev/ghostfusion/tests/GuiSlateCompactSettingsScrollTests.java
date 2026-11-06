package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiChrome;
import dev.ghostfusion.ui.GuiHitZones;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.GuiScrollIndicator;
import dev.ghostfusion.ui.GuiSlateCompactSettingsScroll;
import dev.ghostfusion.ui.UiState;
import java.util.Random;

/** 50k small-viewport, overflow, disjoint pointer and scroll parity checks. */
public final class GuiSlateCompactSettingsScrollTests {
    private static long checks;
    private static void ok(boolean b,String name) {
        checks++;if(!b)throw new AssertionError(name);
    }
    private static void fails(Runnable r,String name) {
        boolean thrown=false;
        try{r.run();}catch(IllegalArgumentException expected){thrown=true;}
        ok(thrown,name);
    }
    public static void main(String[] args) {
        GuiLayout auto=GuiLayout.calculate(480,270,
            UiState.Layout.WIDE,UiState.Theme.SLATE);
        int rows=GuiHitZones.visibleSettings(auto);
        ok(GuiChrome.interactive(auto)&&rows>=1&&rows<=2,
            "Minecraft Auto-scale exposes only one or two settings");
        ok(GuiSlateCompactSettingsScroll.of(auto,rows,0)==null,
            "no misleading scroll rail for visible-only options");
        GuiSlateCompactSettingsScroll.Frame first=
            GuiSlateCompactSettingsScroll.of(auto,11,0);
        GuiSlateCompactSettingsScroll.Frame end=
            GuiSlateCompactSettingsScroll.of(auto,11,11-rows);
        ok(first!=null&&end!=null,"overflow reveals a compact settings rail");
        ok(first.x==auto.x+auto.width-8 && first.width==2,
            "rail occupies the outer margin, not the choice/slider column");
        ok(first.y==GuiHitZones.settingStartY(auto) &&
           first.height==rows*22 &&
           end.thumbY+end.thumbHeight==end.y+end.height,
            "thumb travels from first to last visible option");
        ok(!GuiHitZones.settingsArea(auto,first.x,first.y),
            "rail does not create a second settings hit zone");
        ok(GuiSlateCompactSettingsScroll.of(auto,11,999).thumbY==end.thumbY,
            "out-of-date setting scroll is clamped");
        fails(()->GuiSlateCompactSettingsScroll.of(null,1,0),
            "null layout rejected");
        fails(()->GuiSlateCompactSettingsScroll.of(auto,-1,0),
            "negative setting count rejected");
        fails(()->GuiSlateCompactSettingsScroll.of(auto,1,-1),
            "negative first setting rejected");

        Random random=new Random(2026110103L);
        for(int i=0;i<50000;i++) {
            int sw=1+random.nextInt(2400),sh=1+random.nextInt(1500);
            UiState.Layout mode=random.nextBoolean()?
                UiState.Layout.WIDE:UiState.Layout.COMPACT;
            GuiLayout g=GuiLayout.calculate(sw,sh,mode,UiState.Theme.SLATE);
            int count=random.nextInt(210),top=random.nextInt(250);
            int shown=GuiHitZones.visibleSettings(g);
            GuiSlateCompactSettingsScroll.Frame bar=
                GuiSlateCompactSettingsScroll.of(g,count,top);
            if(!GuiChrome.interactive(g)||shown<1||count<=shown) {
                ok(bar==null,"not painted with no visual overflow "+i);
                continue;
            }
            ok(bar!=null,"visible settings overflow has a track "+i);
            ok(bar.width==2 && bar.x>=g.x+g.width-11 &&
               bar.x+bar.width<=g.x+g.width,
               "track stays within noninteractive right margin "+i);
            ok(!GuiHitZones.settingsArea(g,bar.x,bar.y) &&
               GuiHitZones.settingIndex(g,bar.x,bar.y,0,count)==-1,
               "rail cannot mutate a setting through hitbox overlap "+i);
            ok(bar.y==GuiHitZones.settingStartY(g) &&
               bar.height==shown*22 &&
               bar.y+bar.height<=g.y+g.height-18,
               "track is exactly the painted setting rows "+i);
            ok(bar.thumbHeight>=1 && bar.thumbY>=bar.y &&
               bar.thumbY+bar.thumbHeight<=bar.y+bar.height,
               "scroll thumb is always contained "+i);
            int clamped=Math.min(top,count-shown);
            ok(bar.thumbHeight==GuiScrollIndicator.height(bar.height,count,shown)
                &&bar.thumbY-bar.y==GuiScrollIndicator.offset(
                    bar.height,count,shown,clamped),
                "settings scroll matches shared proportional indicator "+i);
        }
        System.out.println("GUI_SLATE_COMPACT_SETTINGS_SCROLL_PASS checks="+checks);
    }
}
