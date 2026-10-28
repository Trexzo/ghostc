package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSlateCompactSearchGeometry;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.GuiChrome;
import dev.ghostfusion.ui.GuiSlateWorkspace;
import dev.ghostfusion.ui.UiState;
import java.util.Random;

public final class GuiSlateCompactSearchGeometryTests {
    private static long count;
    private static void ok(boolean yes,String reason) {
        count++;if(!yes)throw new AssertionError(reason);
    }
    private static void probe(GuiLayout g) {
        if(!GuiChrome.interactive(g))return;
        int sx=GuiSlateCompactSearchGeometry.surfaceX(g);
        int sy=GuiSlateCompactSearchGeometry.surfaceY(g);
        int sw=GuiSlateCompactSearchGeometry.surfaceWidth(g);
        int tx=GuiSlateCompactSearchGeometry.fieldX(g);
        int ty=GuiSlateCompactSearchGeometry.fieldY(g);
        int tw=GuiSlateCompactSearchGeometry.fieldWidth(g);
        int cx=GuiSlateCompactSearchGeometry.clearX(g);
        int cy=GuiSlateCompactSearchGeometry.clearY(g);
        ok(sx>=g.x+g.sidebar && sx+sw<=g.x+g.width,
           "search surface stays in module content panel");
        ok(sy>=g.y+30&&sy+21<=g.listY,
           "search surface does not collide with header or module list");
        ok(tx>=sx+5&&tx+tw+7<=cx,
           "native textbox caret remains outside clear control");
        ok(ty>=sy&&ty+17<=sy+21,
           "native input fits its custom surface");
        ok(cx+14<=sx+sw&&cy+15<=sy+21,
           "clear control contained");
        ok(GuiSlateCompactSearchGeometry.surfaceHit(g,sx,sy),
           "first surface pixel focuses");
        ok(!GuiSlateCompactSearchGeometry.surfaceHit(g,sx+sw,sy),
           "right edge exclusive");
        ok(GuiSlateCompactSearchGeometry.clearHit(g,cx,cy),
           "first clear pixel");
        ok(!GuiSlateCompactSearchGeometry.clearHit(g,tx+2,ty+5),
           "clicking input never clears text");
        ok(!GuiSlateCompactSearchGeometry.clearHit(g,cx+14,cy),
           "clear hit right edge exclusive");
    }
    public static void main(String[] args) {
        UiState ui=new UiState();
        ui.theme(UiState.Theme.SLATE);
        GuiLayout auto=GuiLayout.calculate(480,270,UiState.Layout.WIDE,ui.theme());
        ok(GuiSlateWorkspace.of(auto,ui)==null && GuiChrome.interactive(auto),
           "1080p Auto uses compact interactive search");
        probe(auto);
        Random random=new Random(28102026L);
        for(int i=0;i<40000;i++){
            GuiLayout g=GuiLayout.calculate(1+random.nextInt(2700),
                1+random.nextInt(1700),UiState.Layout.WIDE,ui.theme());
            if(GuiSlateWorkspace.of(g,ui)==null)probe(g);
        }
        System.out.println("GUI_SLATE_COMPACT_SEARCH_PASS checks="+count);
    }
}
