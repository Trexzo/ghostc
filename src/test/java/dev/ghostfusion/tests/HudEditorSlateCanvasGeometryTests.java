package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudEditorControls;
import dev.ghostfusion.ui.HudEditorSlateCanvasGeometry;
import dev.ghostfusion.ui.HudPreviewGeometry;
import dev.ghostfusion.ui.HudPositions;
import dev.ghostfusion.ui.UiState;
import java.util.Random;

/** Real letterbox/canvas rectangles; 50k deterministic visual-only probes. */
public final class HudEditorSlateCanvasGeometryTests {
    private static long checks;
    private static void ok(boolean good,String why) {
        checks++;if(!good)throw new AssertionError(why);
    }
    private static void reject(Runnable r,String why) {
        boolean error=false;
        try{r.run();}catch(IllegalArgumentException expected){error=true;}
        ok(error,why);
    }
    public static void main(String[] args) {
        UiState ui=new UiState();
        HudEditorControls.Item[] items=HudEditorControls.Item.values();
        Random random=new Random(29102026L);
        for(int i=0;i<50000;i++) {
            int pw=1+random.nextInt(1100),ph=1+random.nextInt(900);
            int sw=1+random.nextInt(3800),sh=1+random.nextInt(2400);
            HudPreviewGeometry g=new HudPreviewGeometry(
                13,25,pw,ph,sw,sh);
            HudPreviewGeometry.Rect canvas=g.canvas;
            int cx=HudEditorSlateCanvasGeometry.centerX(canvas);
            int cy=HudEditorSlateCanvasGeometry.centerY(canvas);
            ok(cx>=canvas.x&&cx<canvas.x+canvas.width,
                "center guide within letterbox");
            ok(cy>=canvas.y&&cy<canvas.y+canvas.height,
                "horizontal center within letterbox");
            for(int third=1;third<=2;third++) {
                int gx=HudEditorSlateCanvasGeometry.gridX(canvas,third);
                int gy=HudEditorSlateCanvasGeometry.gridY(canvas,third);
                ok(gx==-1||gx>canvas.x&&gx<canvas.x+canvas.width,
                    "third x strictly inside canvas");
                ok(gy==-1||gy>canvas.y&&gy<canvas.y+canvas.height,
                    "third y strictly inside canvas");
            }
            HudPreviewGeometry.Rect[] rects=
                new HudPreviewGeometry.Rect[items.length];
            rects[0]=g.status(ui,124,22);
            for(int w=0;w<HudPositions.Widget.values().length;w++) {
                HudPositions.Widget widget=HudPositions.Widget.values()[w];
                rects[w+1]=g.widget(ui.widgets(),widget,widget.width,widget.height);
            }
            for(HudPreviewGeometry.Rect r:rects) {
                int c=HudEditorSlateCanvasGeometry.corner(r);
                ok(c>=1&&c<=7&&c<=r.width&&c<=r.height,
                    "selection brackets never escape tiny preview");
            }
            int mx=canvas.x+random.nextInt(canvas.width);
            int my=canvas.y+random.nextInt(canvas.height);
            HudEditorControls.Item hover=
                HudEditorSlateCanvasGeometry.hovered(canvas,rects,mx,my);
            HudEditorControls.Item oracle=null;
            for(int j=rects.length-1;j>=0;j--) {
                if(rects[j].contains(mx,my)){oracle=items[j];break;}
            }
            ok(hover==oracle,"hover matches actual reverse-paint hit order");
            ok(HudEditorSlateCanvasGeometry.hovered(
                canvas,rects,canvas.x-1,canvas.y)==null,
                "letterbox gutters never select a widget");
            HudEditorControls.Item item=items[i%items.length];
            String detail=HudEditorSlateCanvasGeometry.coordinateLabel(
                item,random.nextDouble(),random.nextDouble());
            ok(detail.startsWith(item.label+"  X ")&&detail.endsWith("%"),
                "truthful selected tab and percentages");
            ok(detail.length()<30,"compact display at native Minecraft font");
        }
        ok(HudEditorSlateCanvasGeometry.coordinateLabel(items[0],0,1)
                .equals("Status  X 0%  Y 100%"),
            "exact saved edge positions");
        reject(()->HudEditorSlateCanvasGeometry.coordinateLabel(items[0],
                Double.NaN,0.5),"nonfinite anchor rejected");
        reject(()->HudEditorSlateCanvasGeometry.coordinateLabel(null,0,0),
                "missing selected widget rejected");
        System.out.println("HUD_EDITOR_SLATE_CANVAS_PASS checks="+checks);
    }
}
