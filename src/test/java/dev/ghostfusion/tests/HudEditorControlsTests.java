package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudEditorControls;
import dev.ghostfusion.ui.HudFootprints;
import dev.ghostfusion.ui.HudPositions;
import dev.ghostfusion.ui.HudPreviewGeometry;
import dev.ghostfusion.ui.UiState;
import java.util.Random;

/** R2.1 selected HUD tabs, exact real render footprint, pixel nudging and reset. */
public final class HudEditorControlsTests {
    private static int checks;
    private static void ok(boolean value,String why) {
        checks++;
        if(!value)throw new AssertionError(why);
    }
    private static void rejects(Runnable action,String why) {
        boolean seen=false;
        try{action.run();}catch(IllegalArgumentException expected){seen=true;}
        ok(seen,why);
    }
    private static void footprint() {
        ok(HudFootprints.height(HudPositions.Widget.KEYSTROKES,false,false,true)==37,
           "keys only movement rows 37 high");
        ok(HudFootprints.height(HudPositions.Widget.KEYSTROKES,true,false,true)==57,
           "jump row adds 20");
        ok(HudFootprints.height(HudPositions.Widget.KEYSTROKES,false,true,true)==57,
           "sneak row adds 20");
        ok(HudFootprints.height(HudPositions.Widget.KEYSTROKES,true,true,true)==77,
           "two optional rows");
        ok(HudFootprints.height(HudPositions.Widget.TARGET,true,true,false)==29,
           "target without health bar");
        ok(HudFootprints.height(HudPositions.Widget.TARGET,true,true,true)==43,
           "target with health");
        ok(HudFootprints.height(HudPositions.Widget.CPS,false,false,false)==17,
           "CPS fixed footprint");
        rejects(()->HudFootprints.width(null),"null widget cannot render");
        rejects(()->HudFootprints.height(null,false,false,false),"null widget size");
        HudPreviewGeometry preview=new HudPreviewGeometry(0,0,300,300,1920,1080);
        UiState state=new UiState();
        for(HudPositions.Widget widget:HudPositions.Widget.values()) {
            int actualHeight=HudFootprints.height(widget,false,false,false);
            HudPreviewGeometry.Rect r=preview.widget(state.widgets(),widget,
                HudFootprints.width(widget),actualHeight);
            ok(r.height==preview.displayHeight(actualHeight),"dynamic preview height parity");
        }
        HudPreviewGeometry.Rect oldTarget=preview.widget(state.widgets(),
            HudPositions.Widget.TARGET,160,43);
        HudPreviewGeometry.Rect newTarget=preview.widget(state.widgets(),
            HudPositions.Widget.TARGET,160,29);
        ok(newTarget.height<oldTarget.height,"target preview shrinks with disabled health");
        rejects(()->preview.widget(state.widgets(),HudPositions.Widget.TARGET,160,0),
            "invalid dynamic footprint");
    }
    private static void selectionAndReset() {
        UiState ui=new UiState();
        HudEditorControls editor=new HudEditorControls();
        ok(editor.selected()==HudEditorControls.Item.STATUS,"status initially selected");
        for(HudEditorControls.Item item:HudEditorControls.Item.values()) {
            editor.select(item);
            ok(editor.selected()==item,"select independently");
            int sw=1920,sh=1080;
            int contentW=item==HudEditorControls.Item.STATUS?124:
                HudFootprints.width(item.widget);
            int contentH=item==HudEditorControls.Item.STATUS?22:
                HudFootprints.height(item.widget,true,true,true);
            editor.nudge(ui,123,-45,sw,sh,contentW,contentH);
            editor.reset(ui);
            if(item==HudEditorControls.Item.STATUS) {
                ok(ui.hudX()==UiState.DEFAULT_HUD_X &&
                   ui.hudY()==UiState.DEFAULT_HUD_Y,"status resets legacy defaults");
            } else {
                HudPositions fresh=new HudPositions();
                ok(ui.widgets().x(item.widget)==fresh.x(item.widget) &&
                   ui.widgets().y(item.widget)==fresh.y(item.widget),
                   "widget resets original anchor "+item);
            }
        }
        rejects(()->editor.select(null),"null selected tab");
        rejects(()->editor.nudge(ui,1,0,0,1080,83,83),"zero screen nudge");
        rejects(()->editor.nudge(ui,1,0,1920,1080,0,83),"zero footprint nudge");
        rejects(()->HudEditorControls.tabLeft(0,0,HudEditorControls.Item.STATUS),
           "tabs require viable width");
        ok(HudEditorControls.itemFor(null)==HudEditorControls.Item.STATUS,
           "null widget means status");
        for(HudPositions.Widget w:HudPositions.Widget.values())
            ok(HudEditorControls.itemFor(w).widget==w,"widget maps to exact tab");
    }
    private static void tabRanges() {
        HudEditorControls.Item[] items=HudEditorControls.Item.values();
        for(int w=40;w<550;w++) {
            int last=170;
            for(int i=0;i<items.length;i++) {
                HudEditorControls.Item item=items[i];
                int x=HudEditorControls.tabLeft(170,w,item);
                int width=HudEditorControls.tabWidth(w,item);
                ok(x==last,"tab regions contiguous, no gaps");
                ok(width>=1,"positive tab width");
                for(int px=x;px<x+width;px++)
                    ok(HudEditorControls.tabAt(170,400,w,px,405)==item,
                       "visual tab matches click geometry "+w+"/"+item);
                ok(HudEditorControls.tabAt(170,400,w,x,399)==null,
                   "tab row not clickable above");
                ok(HudEditorControls.tabAt(170,400,w,x,417)==null,
                   "tab row not clickable below");
                last=x+width;
            }
            ok(last==170+w,"all tab pixels accounted");
            ok(HudEditorControls.tabAt(170,400,w,170+w,405)==null,
               "exclusive right edge");
            ok(HudEditorControls.tabAt(170,400,w,169,405)==null,
               "exclusive left edge");
        }
    }
    private static void nudgeReplay() {
        HudEditorControls editor=new HudEditorControls();
        UiState state=new UiState();
        Random rng=new Random(2026101921L);
        HudEditorControls.Item[] items=HudEditorControls.Item.values();
        for(int i=0;i<24000;i++) {
            HudEditorControls.Item selected=items[rng.nextInt(items.length)];
            editor.select(selected);
            int screenW=1+rng.nextInt(2600), screenH=1+rng.nextInt(1600);
            int footprintW=selected.widget==null?124:HudFootprints.width(selected.widget);
            int footprintH=selected.widget==null?22:HudFootprints.height(
                selected.widget,rng.nextBoolean(),rng.nextBoolean(),rng.nextBoolean());
            int dx=rng.nextInt(61)-30,dy=rng.nextInt(61)-30;
            int x0=selected.widget==null?state.pixelX(screenW,footprintW):
                state.widgets().pixelX(selected.widget,screenW,footprintW);
            int y0=selected.widget==null?state.pixelY(screenH,footprintH):
                state.widgets().pixelY(selected.widget,screenH,footprintH);
            // All other anchors must stay bitwise stable.
            double[] before=new double[8];
            before[0]=state.hudX();before[1]=state.hudY();
            for(HudPositions.Widget widget:HudPositions.Widget.values()) {
                before[2+widget.ordinal()*2]=state.widgets().x(widget);
                before[3+widget.ordinal()*2]=state.widgets().y(widget);
            }
            editor.nudge(state,dx,dy,screenW,screenH,footprintW,footprintH);
            int x1=selected.widget==null?state.pixelX(screenW,footprintW):
                state.widgets().pixelX(selected.widget,screenW,footprintW);
            int y1=selected.widget==null?state.pixelY(screenH,footprintH):
                state.widgets().pixelY(selected.widget,screenH,footprintH);
            int expectedX=Math.max(0,Math.min(Math.max(0,screenW-footprintW),x0+dx));
            int expectedY=Math.max(0,Math.min(Math.max(0,screenH-footprintH),y0+dy));
            ok(Math.abs(x1-expectedX)<=1,"nudge x follows in-game scaled pixels "+i);
            ok(Math.abs(y1-expectedY)<=1,"nudge y follows in-game scaled pixels "+i);
            for(HudEditorControls.Item other:items) {
                if(other==selected)continue;
                if(other.widget==null) {
                    ok(state.hudX()==before[0] && state.hudY()==before[1],
                       "status not moved by unrelated nudge "+i);
                } else {
                    int index=other.widget.ordinal();
                    ok(state.widgets().x(other.widget)==before[2+index*2] &&
                       state.widgets().y(other.widget)==before[3+index*2],
                       "widget not moved by unrelated nudge "+i);
                }
            }
            if(i%27==0)editor.reset(state);
        }
        for(HudEditorControls.Item item:items) {
            editor.select(item);
            editor.nudge(state,Integer.MAX_VALUE,Integer.MIN_VALUE,1920,1080,83,43);
            double x=item.widget==null?state.hudX():state.widgets().x(item.widget);
            double y=item.widget==null?state.hudY():state.widgets().y(item.widget);
            ok(x>=0 && x<=1 && y>=0 && y<=1,
               "integer-extreme safe clamped nudge "+item);
        }
    }
    public static void main(String[] args) {
        footprint();selectionAndReset();tabRanges();nudgeReplay();
        ok(checks>160000,"substantial HUD control test coverage");
        System.out.println("HUD_EDITOR_CONTROLS_PASS assertions="+checks);
    }
}
