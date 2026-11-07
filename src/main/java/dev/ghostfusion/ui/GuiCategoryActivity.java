package dev.ghostfusion.ui;

import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import java.util.Arrays;

/** Read-only, allocation-free counting for the Slate ClickGUI navigation rail. */
public final class GuiCategoryActivity {
    public static final int COUNT=GhostModule.Category.values().length+1;
    private GuiCategoryActivity() {}

    /**
     * Index zero is All; category ordinal+1 matches the existing sidebar.
     * Only truly enabled modules count, never merely configured toggles.
     */
    public static void tally(ModuleRegistry registry,int[] output) {
        if(registry==null || output==null || output.length!=COUNT)
            throw new IllegalArgumentException("Category activity buffer");
        registry.assertOwnerThread();
        Arrays.fill(output,0);
        for(ModuleRegistry.Entry entry:registry.all()) {
            if(entry.state()!=ModuleRegistry.State.ENABLED)continue;
            GhostModule.Category category=entry.module().category();
            if(category==null)continue;
            output[0]++;
            output[category.ordinal()+1]++;
        }
    }

    /** Empty categories should not add visual noise to the navigation rail. */
    public static String label(int enabled) {
        if(enabled<0)throw new IllegalArgumentException("Negative category activity");
        if(enabled==0)return "";
        return enabled>99?"99+":Integer.toString(enabled);
    }
}
