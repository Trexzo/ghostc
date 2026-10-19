package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiChrome;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.UiState;
import java.util.Random;

/** R1.6: header/sidebar hover and mouse hitboxes never overlap or escape the frame. */
public final class GuiChromeTests {
    private static int assertions;
    private static void check(boolean ok,String why) {
        assertions++;
        if(!ok) throw new AssertionError(why);
    }
    private static void verify(GuiLayout g) {
        boolean active=GuiChrome.interactive(g);
        GuiChrome.Header module=GuiChrome.headerAt(g,g.x+g.sidebar+10,g.y+12);
        GuiChrome.Header hud=GuiChrome.headerAt(g,g.x+g.sidebar+85,g.y+12);
        GuiChrome.Header layout=GuiChrome.headerAt(g,g.x+g.width-98,g.y+12);
        GuiChrome.Header theme=GuiChrome.headerAt(g,g.x+g.width-54,g.y+12);
        if(active) {
            check(module==GuiChrome.Header.MODULES,"module nav");
            check(hud==GuiChrome.Header.HUD,"HUD nav");
            check(layout==GuiChrome.Header.LAYOUT,"layout button");
            check(theme==GuiChrome.Header.THEME,"theme button");
            check(module!=hud && hud!=layout && layout!=theme,"distinct header controls");
            check(GuiChrome.headerAt(g,g.x+3,g.y+12)==GuiChrome.Header.NONE,
                "sidebar title not a tab");
            check(GuiChrome.headerAt(g,g.x+g.width,g.y+12)==GuiChrome.Header.NONE,
                "right edge not clickable");
            for(int i=0;i<6;i++) {
                int y=g.y+62+i*23,x=g.x+5;
                check(GuiChrome.categoryIndex(g,x,y)==i,"category first pixel "+i);
                check(GuiChrome.categoryIndex(g,x,y+18)==i,"category last pixel "+i);
                check(GuiChrome.categoryIndex(g,x,y+19)==-1,"category gap start "+i);
                check(GuiChrome.categoryIndex(g,x,y+22)==-1,"category gap end "+i);
            }
            check(GuiChrome.categoryIndex(g,g.x+g.sidebar-4,g.y+62)==-1,
                "right border category");
            check(GuiChrome.categoryIndex(g,g.x+3,g.y+62)==-1,
                "left border category");
            check(GuiChrome.categoryIndex(g,g.x+5,g.y+g.height-23)==-1,
                "footer no categories");
        } else {
            check(module==GuiChrome.Header.NONE &&
                hud==GuiChrome.Header.NONE &&
                layout==GuiChrome.Header.NONE &&
                theme==GuiChrome.Header.NONE,
                "undersized header is noninteractive");
            for(int i=0;i<6;i++)
                check(GuiChrome.categoryIndex(g,g.x+5,g.y+62+i*23)==-1,
                    "undersized category cannot activate "+i);
        }
    }
    private static void boundaries() {
        for(UiState.Layout mode:UiState.Layout.values()) {
            for(int w=1;w<=960;w+=9) {
                for(int h=1;h<=680;h+=11) {
                    GuiLayout g=GuiLayout.calculate(w,h,mode);
                    check(g.insidePanel(w,h),"chrome fits viewport");
                    verify(g);
                }
            }
        }
        Random random=new Random(0x162019L);
        for(int i=0;i<18000;i++) {
            int w=1+random.nextInt(3840),h=1+random.nextInt(2160);
            verify(GuiLayout.calculate(w,h,(i%2)==0?
                UiState.Layout.WIDE:UiState.Layout.COMPACT));
        }
    }
    public static void main(String[] args) {
        boundaries();
        check(assertions>250000,"large category hitbox replay");
        System.out.println("GUI_CHROME_PASS assertions="+assertions);
    }
}
