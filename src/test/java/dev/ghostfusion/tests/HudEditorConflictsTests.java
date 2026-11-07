package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudEditorConflicts;
import dev.ghostfusion.ui.HudEditorControls;
import dev.ghostfusion.ui.HudOverlapPolicy;
import dev.ghostfusion.ui.HudPreviewGeometry;
import java.util.Random;

/** 40k seeded in-canvas and edge-touching conflict classifications. */
public final class HudEditorConflictsTests {
    private static long checks;
    private static void ok(boolean value,String label) {
        checks++;
        if(!value)throw new AssertionError(label);
    }
    private static void rejects(Runnable r,String label) {
        boolean threw=false;
        try {r.run();}catch(IllegalArgumentException expected){threw=true;}
        ok(threw,label);
    }
    private static HudPreviewGeometry.Rect rect(int x,int y,int w,int h) {
        return new HudPreviewGeometry(x,y,w,h,w,h).canvas;
    }
    public static void main(String[] args) {
        HudEditorControls.Item[] items=HudEditorControls.Item.values();
        HudPreviewGeometry.Rect[] rects=new HudPreviewGeometry.Rect[items.length];
        rects[0]=rect(0,0,30,20);
        rects[1]=rect(5,5,10,10);
        rects[2]=rect(30,0,30,20); // shares an edge, not a collision
        rects[3]=rect(10,10,20,10);
        rects[4]=rect(100,100,20,20);
        int mask=HudEditorConflicts.mask(HudEditorControls.Item.STATUS,rects);
        ok(Integer.bitCount(mask)==2,"two colliding peers, no edge-only match");
        ok(HudEditorConflicts.includes(mask,HudEditorControls.Item.KEYS),
           "keys collision is identified");
        ok(!HudEditorConflicts.includes(mask,HudEditorControls.Item.CPS),
           "edge-only CPS does not count");
        ok(HudEditorConflicts.message(HudEditorControls.Item.STATUS,mask)
             .equals("Status overlaps Keys +1"),
           "named summary with further-collision count");
        ok(HudEditorConflicts.message(HudEditorControls.Item.CPS,0).isEmpty(),
           "clean canvas has no warning");
        rejects(()->HudEditorConflicts.mask(null,rects),"no null selection");
        rejects(()->HudEditorConflicts.mask(items[0],new HudPreviewGeometry.Rect[2]),
            "no incomplete footprint buffers");
        rejects(()->HudEditorConflicts.message(items[0],1),
            "never describe selected item overlapping itself");
        rejects(()->HudEditorConflicts.includes(-1,items[0]),"no invalid mask");
        int statusBit=1<<HudEditorControls.Item.STATUS.ordinal();
        int keysBit=1<<HudEditorControls.Item.KEYS.ordinal();
        int targetBit=1<<HudEditorControls.Item.TARGET.ordinal();
        ok(HudEditorConflicts.activeMask(items[0],mask,statusBit)==0,
            "disabled peer is not reported as a live HUD collision");
        ok(HudEditorConflicts.activeMask(items[0],mask,keysBit)==0,
            "disabled selected HUD cannot produce a live overlap warning");
        ok(HudEditorConflicts.activeMask(items[0],mask,
            statusBit|keysBit)==keysBit,
            "enabled selected HUD reports only its enabled peers");
        ok(HudEditorConflicts.activeMask(items[0],mask,
            statusBit|keysBit|targetBit)==(keysBit|targetBit),
            "both actually enabled peers retain the accurate warning");
        rejects(()->HudEditorConflicts.activeMask(null,mask,1),
            "active mask needs a selected widget");
        rejects(()->HudEditorConflicts.activeMask(items[0],mask,1<<9),
            "unknown active mask bits rejected");
        rejects(()->HudEditorConflicts.activeMask(items[0],statusBit,statusBit),
            "self collision bit rejected");
        rejects(()->HudEditorConflicts.activeMask(items[0],-1,0),
            "negative geometry mask rejected");
        Random rng=new Random(20261028L);
        for(int i=0;i<40000;i++) {
            for(int j=0;j<rects.length;j++) {
                rects[j]=rect(rng.nextInt(500)-250,rng.nextInt(500)-250,
                              1+rng.nextInt(180),1+rng.nextInt(130));
            }
            HudEditorControls.Item selected=items[rng.nextInt(items.length)];
            int bits=HudEditorConflicts.mask(selected,rects),oracle=0;
            for(int j=0;j<items.length;j++) {
                if(items[j]!=selected &&
                   HudOverlapPolicy.overlaps(rects[selected.ordinal()],rects[j]))
                    oracle|=1<<j;
            }
            ok(bits==oracle,"pixel oracle agrees "+i);
            ok(!HudEditorConflicts.includes(bits,selected),"self bit absent "+i);
            int activeBits=rng.nextInt(1<<items.length);
            int live=HudEditorConflicts.activeMask(selected,bits,activeBits);
            int expected=(activeBits&(1<<selected.ordinal()))==0?0:
                (bits&activeBits);
            ok(live==expected,
                "40k enabled-only real HUD collision masks agree "+i);
            ok((live&~bits)==0,"filter never creates false overlap "+i);
            if((activeBits&(1<<selected.ordinal()))==0)
                ok(live==0,"disabled selected widget has no red warning "+i);
            String message=HudEditorConflicts.message(selected,bits);
            ok((bits==0)==message.isEmpty(),"warning iff overlapping "+i);
            if(bits!=0) {
                ok(message.startsWith(selected.label+" overlaps "),
                    "warning names selected item "+i);
                if(Integer.bitCount(bits)>1)
                    ok(message.endsWith("+"+(Integer.bitCount(bits)-1)),
                        "warning names additional collisions "+i);
            }
        }
        System.out.println("HUD_EDITOR_CONFLICTS_PASS checks="+checks);
    }
}
