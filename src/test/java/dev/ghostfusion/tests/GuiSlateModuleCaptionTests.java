package dev.ghostfusion.tests;

import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.ui.GuiSlateModuleCaption;
import dev.ghostfusion.ui.HudTextFit;
import java.util.Random;
import java.util.function.ToIntFunction;

/** Reference-informed title/secondary metadata safety, no Minecraft runtime. */
public final class GuiSlateModuleCaptionTests {
    private static long checks;
    private static void ok(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
    public static void main(String[] args) {
        for(GhostModule.Category cat:GhostModule.Category.values()) {
            String name=GuiSlateModuleCaption.category(cat);
            ok(name.length()>3&&name.length()<15,"short category label");
            ok(GuiSlateModuleCaption.of(cat,null).equals(name),
                "unbound module shows category only");
            ok(GuiSlateModuleCaption.of(cat,"").equals(name),
                "empty shortcut not advertised");
            ok(GuiSlateModuleCaption.of(cat,"LShift").equals(name+" / LSHIFT"),
                "true keybind rendered as secondary metadata");
        }
        boolean reject=false;
        try{GuiSlateModuleCaption.of(null,"R");}
        catch(IllegalArgumentException expected){reject=true;}
        ok(reject,"no fabricated category");
        Random rng=new Random(28102026L);
        GhostModule.Category[] cats=GhostModule.Category.values();
        ToIntFunction<String> measured=new ToIntFunction<String>() {
            public int applyAsInt(String value){return value.codePointCount(0,value.length())*6;}
        };
        for(int i=0;i<40000;i++) {
            StringBuilder raw=new StringBuilder();
            int n=rng.nextInt(45);
            for(int j=0;j<n;j++)
                raw.append((char)(32+rng.nextInt(95)));
            GhostModule.Category category=cats[rng.nextInt(cats.length)];
            String caption=GuiSlateModuleCaption.of(category,raw.toString());
            ok(caption.startsWith(GuiSlateModuleCaption.category(category)),
                "category never loses its label");
            ok(caption.length()<=GuiSlateModuleCaption.category(category).length()+15,
                "bounded shortcut and separator");
            for(int j=0;j<caption.length();j++) {
                char ch=caption.charAt(j);
                ok(ch>=32&&ch<127,"ASCII-safe second line");
            }
            String fitted=HudTextFit.fit(caption,115,measured);
            ok(measured.applyAsInt(fitted)<=115,"fits current row's font budget");
        }
        System.out.println("GUI_SLATE_MODULE_CAPTION_PASS checks="+checks);
    }
}
