package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudEditorControls;
import dev.ghostfusion.ui.HudDragCapture;
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
    private static void changedAwareActions() {
        UiState ui=new UiState();
        HudEditorControls controls=new HudEditorControls();
        controls.select(HudEditorControls.Item.STATUS);
        ok(!controls.resetIfChanged(ui),"reset already default status is no-op");
        ok(!controls.nudgeIfChanged(ui,0,0,1920,1080,124,22),
            "zero nudge never quantizes current anchor");
        double beforeY=ui.hudY();
        ok(controls.nudgeIfChanged(ui,10,0,1920,1080,124,22),
            "real horizontal nudge changes selected anchor");
        ok(Double.compare(beforeY,ui.hudY())==0,
            "horizontal nudge preserves exact untouched Y");
        ok(controls.resetIfChanged(ui),"changed status reset returns true");
        ok(!controls.resetIfChanged(ui),"repeated status reset returns false");
        for(HudEditorControls.Item item:HudEditorControls.Item.values()){
            controls.select(item);
            if(item.widget!=null) {
                ok(!controls.resetIfChanged(ui),
                    "already default widget reset is no-op");
                double priorY=ui.widgets().y(item.widget);
                ok(!controls.nudgeIfChanged(ui,0,0,1024,768,83,43),
                    "widget zero nudge is a no-op");
                ok(Double.compare(ui.widgets().y(item.widget),priorY)==0,
                    "zero nudge does not reserialize widget anchor");
            }
            controls.nudge(ui,Integer.MAX_VALUE,Integer.MAX_VALUE,1920,1080,83,43);
            ok(!controls.nudgeIfChanged(ui,10,10,1920,1080,83,43),
                "boundary nudge performs no mutation");
            ok(controls.resetIfChanged(ui),
                "reset from boundary changes only selected anchor");
        }
        rejects(()->controls.nudgeIfChanged(ui,1,0,0,100,83,43),
            "invalid dimensions rejected by changed-aware API");
    }
    private static void capturedHudTransactions() throws Exception {
        UiState ui=new UiState();
        HudDragCapture drag=new HudDragCapture();
        ok(!drag.active() && !drag.finish(ui),
            "idle finish does not request disk persistence");
        drag.begin(ui,null,1920,1080);
        ok(drag.sameViewport(1920,1080) &&
           !drag.sameViewport(1280,720),"capture binds source viewport");
        rejects(()->drag.begin(ui,null,1920,1080),
            "nested drag refused without corrupting original");
        ui.position(0.8,0.8);
        ui.position(UiState.DEFAULT_HUD_X,UiState.DEFAULT_HUD_Y);
        ok(!drag.finish(ui),"return to same status anchor suppresses write");
        ok(!drag.finish(ui),"finished capture cannot commit twice");
        drag.begin(ui,null,1920,1080);
        ui.position(0.45,0.61);
        ok(drag.finish(ui),"status change requests one persisted commit");
        HudPositions.Widget widget=HudPositions.Widget.TARGET;
        drag.begin(ui,widget,1280,720);
        ui.widgets().position(widget,0.31,0.79);
        ok(drag.finish(ui),"widget move requests persisted commit");
        drag.begin(ui,widget,1280,720);
        double firstX=ui.widgets().x(widget),firstY=ui.widgets().y(widget);
        ui.widgets().position(widget,0.8,0.8);
        ui.widgets().position(widget,firstX,firstY);
        ok(!drag.finish(ui),"widget revert suppresses write");
        rejects(()->drag.begin(ui,widget,0,720),
            "zero width viewport invalid");
        rejects(()->drag.begin(null,widget,100,100),
            "null UI invalid");
        drag.begin(ui,widget,100,100);
        java.util.concurrent.atomic.AtomicBoolean rejected=
            new java.util.concurrent.atomic.AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{drag.finish(ui);}catch(IllegalStateException e){rejected.set(true);}
        });
        foreign.start();foreign.join();
        ok(rejected.get()&&drag.active(),
            "foreign-thread finish must not release another thread's drag");
        ok(!drag.finish(ui),"captured default can be released");
    }
    private static void randomizedCapture() {
        java.util.Random rng=new java.util.Random(2772026L);
        UiState ui=new UiState();
        HudDragCapture capture=new HudDragCapture();
        HudPositions.Widget[] widgets=HudPositions.Widget.values();
        int wouldCommit=0;
        for(int i=0;i<80000;i++){
            HudPositions.Widget widget=i%4==0?null:widgets[rng.nextInt(widgets.length)];
            double x=widget==null?ui.hudX():ui.widgets().x(widget);
            double y=widget==null?ui.hudY():ui.widgets().y(widget);
            int w=1+rng.nextInt(3840),h=1+rng.nextInt(2160);
            capture.begin(ui,widget,w,h);
            int steps=1+rng.nextInt(6);
            for(int k=0;k<steps;k++) {
                double nx=rng.nextInt(1001)/1000.0,ny=rng.nextInt(1001)/1000.0;
                if(widget==null)ui.position(nx,ny);
                else ui.widgets().position(widget,nx,ny);
            }
            if(rng.nextBoolean()){
                if(widget==null)ui.position(x,y);
                else ui.widgets().position(widget,x,y);
            }
            double finalX=widget==null?ui.hudX():ui.widgets().x(widget);
            double finalY=widget==null?ui.hudY():ui.widgets().y(widget);
            boolean expected=Double.compare(x,finalX)!=0 ||
                             Double.compare(y,finalY)!=0;
            if(expected)wouldCommit++;
            ok(capture.finish(ui)==expected,
                "capture final-state oracle matches disk-save decision "+i);
            ok(!capture.active()&&!capture.finish(ui),
                "finished drag cannot save twice "+i);
        }
        ok(wouldCommit>25000 && wouldCommit<60000,
            "seed exercises real changes and reverted gestures");
    }
    public static void main(String[] args) {
        footprint();selectionAndReset();tabRanges();nudgeReplay();
        changedAwareActions();capturedHudTransactions();randomizedCapture();
        ok(checks>160000,"substantial HUD control test coverage");
        System.out.println("HUD_EDITOR_CONTROLS_PASS assertions="+checks);
    }
}
