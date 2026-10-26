package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudPositions;
import dev.ghostfusion.ui.HudPreviewGeometry;
import dev.ghostfusion.ui.UiState;
import java.util.Random;

/** R2.0: exact screen-aspect HUD editor preview and safe drag parity. */
public final class HudPreviewTests {
    private static int checks;
    private static void ok(boolean yes,String why) {
        checks++;
        if(!yes) throw new AssertionError(why);
    }
    private static void rejects(Runnable r,String reason) {
        boolean threw=false;
        try{r.run();}catch(IllegalArgumentException expected){threw=true;}
        ok(threw,reason);
    }
    private static void inside(HudPreviewGeometry.Rect r,HudPreviewGeometry.Rect c,
                               String context) {
        ok(r.width>=1 && r.height>=1,context+" positive footprint");
        ok(r.x>=c.x && r.y>=c.y,context+" within top/left");
        ok((long)r.x+r.width<=(long)c.x+c.width &&
           (long)r.y+r.height<=(long)c.y+c.height,context+" within bottom/right");
        ok(r.contains(r.x,r.y),context+" top/left clickable");
        ok(!r.contains(r.x+r.width,r.y),context+" exclusive right");
        ok(!r.contains(r.x,r.y+r.height),context+" exclusive bottom");
    }
    private static void exact() {
        HudPreviewGeometry g=new HudPreviewGeometry(100,100,300,300,1920,1080);
        ok(g.canvas.x==100 && g.canvas.width==300,"widescreen fills preview width");
        ok(g.canvas.height==168 && g.canvas.y==166,"widescreen letterbox instead of tall distortion");
        UiState ui=new UiState();
        HudPositions pos=ui.widgets();
        HudPreviewGeometry.Rect status=g.status(ui,80,15);
        inside(status,g.canvas,"status");
        for(HudPositions.Widget widget:HudPositions.Widget.values()) {
            HudPreviewGeometry.Rect r=g.widget(pos,widget);
            inside(r,g.canvas,widget.name());
            ok(r.width==g.displayWidth(widget.width) &&
               r.height==g.displayHeight(widget.height),"widget scaled footprint");
        }
        HudPreviewGeometry.Rect moved=g.widget(pos,HudPositions.Widget.CPS);
        g.moveWidget(pos,HudPositions.Widget.CPS,g.canvas.x,g.canvas.y,
            0,0);
        ok(pos.x(HudPositions.Widget.CPS)==0 && pos.y(HudPositions.Widget.CPS)==0,
           "CPS moves to upper-left");
        g.moveWidget(pos,HudPositions.Widget.CPS,g.canvas.x+999,g.canvas.y+999,
            0,0);
        ok(pos.x(HudPositions.Widget.CPS)==1 && pos.y(HudPositions.Widget.CPS)==1,
           "CPS position clamps to lower-right");
        ok(g.widget(pos,HudPositions.Widget.CPS).x>=moved.x,
           "new CPS position follows mouse");
        g.moveStatus(ui,80,15,-999,-999,0,0);
        ok(ui.hudX()==0 && ui.hudY()==0,"status clamps to upper-left");
        g.moveStatus(ui,80,15,9999,9999,0,0);
        ok(ui.hudX()==1 && ui.hudY()==1,"status clamps to lower-right");
        rejects(()->new HudPreviewGeometry(0,0,0,400,1920,1080),
            "zero editor width rejected");
        rejects(()->new HudPreviewGeometry(0,0,300,300,0,1080),
            "zero scaled screen rejected");
        HudPreviewGeometry.Rect atCenter=g.at(0.5,0.5,160,43);
        ok(Math.abs((2*atCenter.x+atCenter.width)-
            (2*g.canvas.x+g.canvas.width))<=1,"normalized center preview parity");
        HudPreviewGeometry.Rect atEnd=g.at(1.0,1.0,160,43);
        ok(atEnd.x+atEnd.width==g.canvas.x+g.canvas.width &&
           atEnd.y+atEnd.height==g.canvas.y+g.canvas.height,
           "normalized lower right touches editor bounds");
        rejects(()->g.at(Double.NaN,0.5,160,43),
            "nonfinite direct preview anchors rejected");
        rejects(()->g.displayWidth(0),"zero element width rejected");
        rejects(()->g.displayHeight(-5),"negative element height rejected");
        rejects(()->g.widget(null,HudPositions.Widget.CPS),"null anchors rejected");
        rejects(()->g.widget(pos,null),"null widget rejected");
        rejects(()->g.status(null,80,15),"null UI rejected");
    }
    private static void seeded() {
        Random random=new Random(2026101920L);
        HudPositions.Widget[] widgets=HudPositions.Widget.values();
        for(int i=0;i<18000;i++) {
            int screenW=1+random.nextInt(3840),screenH=1+random.nextInt(2160);
            int panelW=1+random.nextInt(400),panelH=1+random.nextInt(340);
            int px=random.nextInt(1000)-500,py=random.nextInt(1000)-500;
            HudPreviewGeometry g=new HudPreviewGeometry(px,py,panelW,panelH,
                screenW,screenH);
            HudPreviewGeometry.Rect c=g.canvas;
            ok(c.x>=px&&c.y>=py,"letterbox origin inside panel "+i);
            ok(c.width<=panelW&&c.height<=panelH,"letterbox bounded "+i);
            ok((long)c.x+c.width<=(long)px+panelW &&
               (long)c.y+c.height<=(long)py+panelH,"letterbox end inside "+i);
            UiState ui=new UiState();
            double initialStatusX=ui.hudX(),initialStatusY=ui.hudY();
            HudPreviewGeometry.Rect status=g.status(ui,124,22);
            inside(status,c,"status "+i);
            for(HudPositions.Widget widget:widgets) {
                HudPreviewGeometry.Rect before=g.widget(ui.widgets(),widget);
                inside(before,c,"widget "+widget.name()+" "+i);
                double x0=ui.widgets().x(widget),y0=ui.widgets().y(widget);
                // Move an arbitrary amount and verify the same canvas is used
                // for setting and drawing normalized coordinates.
                int dx=random.nextInt(61)-30,dy=random.nextInt(61)-30;
                g.moveWidget(ui.widgets(),widget,before.x+dx,before.y+dy,0,0);
                HudPreviewGeometry.Rect after=g.widget(ui.widgets(),widget);
                inside(after,c,"moved "+widget.name()+" "+i);
                int expectX=Math.max(c.x,Math.min(c.x+c.width-after.width,before.x+dx));
                int expectY=Math.max(c.y,Math.min(c.y+c.height-after.height,before.y+dy));
                ok(Math.abs(after.x-expectX)<=1 && Math.abs(after.y-expectY)<=1,
                   "drag coordinate follows the drawn screen "+i);
                ok(x0>=0&&y0>=0,"default normalized values "+i);
            }
            ok(ui.hudX()==initialStatusX && ui.hudY()==initialStatusY,
               "feature widget drag cannot move status "+i);
            int fakeW=1+random.nextInt(300),fakeH=1+random.nextInt(60);
            HudPreviewGeometry.Rect baseline=g.status(ui,fakeW,fakeH);
            int dragX=random.nextInt(50)-20,dragY=random.nextInt(50)-20;
            g.moveStatus(ui,fakeW,fakeH,baseline.x+dragX,baseline.y+dragY,0,0);
            HudPreviewGeometry.Rect moved=g.status(ui,fakeW,fakeH);
            inside(moved,c,"status dragged "+i);
            ok(Math.abs(moved.x-Math.max(c.x,Math.min(c.x+c.width-moved.width,
                baseline.x+dragX)))<=1,"status x preview parity "+i);
            ok(Math.abs(moved.y-Math.max(c.y,Math.min(c.y+c.height-moved.height,
                baseline.y+dragY)))<=1,"status y preview parity "+i);
        }
    }
    public static void main(String[] args) {
        exact();seeded();
        ok(checks>400000,"large aspect+drag replay");
        System.out.println("HUD_PREVIEW_PASS assertions="+checks);
    }
}
