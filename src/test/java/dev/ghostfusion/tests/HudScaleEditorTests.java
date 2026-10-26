package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudScaleEditor;
import dev.ghostfusion.ui.HudEditorControls;
import java.util.Random;

public final class HudScaleEditorTests {
    private static int checks;
    private static void ok(boolean value,String what) {
        checks++;if(!value)throw new AssertionError(what);
    }
    public static void main(String[] args) {
        String[] modules={"hud.status","hud.keystrokes","hud.cps","hud.target","hud.modulelist"};
        HudEditorControls.Item[] items=HudEditorControls.Item.values();
        ok(items.length==modules.length,"every editor tab exposes its module scale");
        for(int i=0;i<items.length;i++)
            ok(modules[i].equals(HudScaleEditor.moduleId(items[i])),
                "scale shortcut targets only selected visual widget "+items[i]);
        ok(HudScaleEditor.adjust(1.0,1,false)==1.05,"fine bracket zoom in");
        ok(HudScaleEditor.adjust(1.0,-1,false)==0.95,"fine bracket zoom out");
        ok(HudScaleEditor.adjust(1.0,1,true)==1.25,"coarse shift bracket");
        ok(HudScaleEditor.adjust(.75,-1,true)==.75,"lower bound stable");
        ok(HudScaleEditor.adjust(1.75,1,true)==1.75,"upper bound stable");
        ok(HudScaleEditor.fraction(.75)==0&&HudScaleEditor.fraction(1.75)==1,
            "settings gateway uses exact slider endpoints");
        Random rng=new Random(20261026);
        for(int i=0;i<50000;i++) {
            double current=(75+rng.nextInt(101))/100.0;
            double next=HudScaleEditor.adjust(current,rng.nextBoolean()?1:-1,
                rng.nextBoolean());
            ok(next>=.75&&next<=1.75,"bounded keyboard zoom "+i);
            double fraction=HudScaleEditor.fraction(next);
            ok(fraction>=0&&fraction<=1,"valid UI fraction "+i);
        }
        System.out.println("HUD_SCALE_EDITOR_PASS checks="+checks);
    }
}
