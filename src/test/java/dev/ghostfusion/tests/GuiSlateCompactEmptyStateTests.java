package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiChrome;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.GuiSlateCompactEmptyState;
import dev.ghostfusion.ui.GuiSlateWorkspace;
import dev.ghostfusion.ui.UiState;
import java.util.Random;
import java.util.function.ToIntFunction;

/** 40k seeded compact/fallback viewports and measured full-label checks. */
public final class GuiSlateCompactEmptyStateTests {
    private static long checks;
    private static void ok(boolean actual,String why) {
        checks++;if(!actual)throw new AssertionError(why);
    }
    private static void invalid(Runnable r,String why) {
        boolean failed=false;
        try{r.run();}catch(IllegalArgumentException expected){failed=true;}
        ok(failed,why);
    }
    public static void main(String[] args) {
        UiState ui=new UiState();
        ui.theme(UiState.Theme.SLATE);
        GuiLayout auto=GuiLayout.calculate(480,270,UiState.Layout.WIDE,ui.theme());
        ok(GuiChrome.interactive(auto)&&GuiSlateWorkspace.of(auto,ui)==null,
            "Auto GUI Scale has interactive Slate fallback");
        GuiSlateCompactEmptyState.Frame sample=GuiSlateCompactEmptyState.of(auto);
        ok(sample!=null,"compact fallback provides honest no-results card");
        ToIntFunction<String> six=s->s.length()*6;
        GuiSlateCompactEmptyState.Copy search=
            GuiSlateCompactEmptyState.copy(sample,true,six);
        GuiSlateCompactEmptyState.Copy category=
            GuiSlateCompactEmptyState.copy(sample,false,six);
        ok(search.title.equals("NO RESULTS")&&search.hint.equals("CHANGE SEARCH"),
            "search empty copy is specific and fully measured");
        ok(category.title.equals("NO RESULTS")&&category.hint.equals("CHANGE CATEGORY"),
            "category empty copy is specific and fully measured");
        ok(GuiSlateCompactEmptyState.copy(sample,true,s->10000)==null,
            "unusually large native font skips captions instead of clipping");
        invalid(()->GuiSlateCompactEmptyState.of(null),"null layout");
        invalid(()->GuiSlateCompactEmptyState.copy(null,true,six),"null frame");
        invalid(()->GuiSlateCompactEmptyState.copy(sample,false,null),"null font");
        invalid(()->GuiSlateCompactEmptyState.copy(sample,true,s->-1),
            "invalid native font width");
        Random rng=new Random(1112026L);
        int admitted=0;
        for(int i=0;i<40000;i++) {
            UiState.Layout style=(i&1)==0?UiState.Layout.WIDE:
                UiState.Layout.COMPACT;
            ui.layout(style);
            GuiLayout g=GuiLayout.calculate(1+rng.nextInt(2700),
                1+rng.nextInt(1800),style,ui.theme());
            if(GuiSlateWorkspace.of(g,ui)!=null)continue;
            GuiSlateCompactEmptyState.Frame f=GuiSlateCompactEmptyState.of(g);
            if(!GuiChrome.interactive(g)) {
                ok(f==null,"tiny noninteractive UI cannot show the card");
                continue;
            }
            ok(f!=null,"interactive compact Slate has a visible empty card");
            admitted++;
            int left=g.x+g.sidebar+11,right=g.x+g.width-11;
            ok(f.x>=left&&f.x+f.width<=right,
                "card is horizontally inside actual module list");
            ok(f.y>=g.listY&&f.y+f.height<=g.listY+g.visibleRows*28,
                "card is vertically inside visible module rows, not settings");
            ok(f.width<=156&&f.height==56,
                "card always uses restrained bounded Slate dimensions");
            ok(f.titleX>=f.x+12&&f.titleY>=f.y+13&&
               f.hintY+9<=f.y+f.height,
                "captions are inset and below divider without clipping");
            int glyph=3+rng.nextInt(11);
            ToIntFunction<String> font=s->s.length()*glyph;
            boolean searching=(i&2)==0;
            GuiSlateCompactEmptyState.Copy copy=
                GuiSlateCompactEmptyState.copy(f,searching,font);
            if(copy!=null) {
                ok(font.applyAsInt(copy.title)<=f.width-24 &&
                   font.applyAsInt(copy.hint)<=f.width-24,
                    "real measured captions fit horizontally");
                ok(!copy.title.contains("...")&&!copy.hint.contains("..."),
                    "no misleading clipped partial captions");
                if(searching)ok(copy.hint.contains("SEARCH"),
                    "search-specific caption never suggests category");
                else ok(copy.hint.equals("FILTER")||copy.hint.contains("CATEGORY"),
                    "category-specific caption does not suggest search");
            }
        }
        ok(admitted>3000,"significant compact and GUI-Auto coverage");
        System.out.println("GUI_SLATE_COMPACT_EMPTY_STATE_PASS checks="+checks);
    }
}
