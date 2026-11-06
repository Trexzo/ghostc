package dev.ghostfusion.ui;

import dev.ghostfusion.core.GhostModule;
import java.util.Locale;

/**
 * Concise, truthful second-line metadata for Slate's module list.
 * Rise-style visual hierarchy without pretending the registry contains
 * marketing descriptions or mutating any module state.
 */
public final class GuiSlateModuleCaption {
    private GuiSlateModuleCaption() {}

    public static String category(GhostModule.Category category) {
        if(category==null)throw new IllegalArgumentException("module category");
        switch(category) {
            case COMBAT: return "Combat";
            case MOVEMENT: return "Movement";
            case WORLD: return "World";
            case RENDER: return "Render";
            case UTILITY: return "Utility";
            default: throw new IllegalArgumentException("unknown module category");
        }
    }
    /** No shortcut is not an error and should not add an UNBOUND badge. */
    public static String of(GhostModule.Category category,String keyName) {
        String label=category(category);
        if(keyName==null || keyName.trim().isEmpty())return label;
        // Keyboard.getKeyName comes from LWJGL; bound its display length and
        // strip controls even if a future remapping returns unusual text.
        StringBuilder key=new StringBuilder(16);
        for(int i=0;i<keyName.length()&&key.length()<12;i++) {
            char ch=keyName.charAt(i);
            if((ch>='A'&&ch<='Z')||(ch>='0'&&ch<='9')||
               (ch>='a'&&ch<='z'))
                key.append(Character.toUpperCase(ch));
        }
        return key.length()==0?label:label+" / "+key.toString();
    }
}
