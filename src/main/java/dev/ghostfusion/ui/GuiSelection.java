package dev.ghostfusion.ui;

import dev.ghostfusion.core.ModuleRegistry;
import java.util.List;

/** A ClickGUI selection is valid only while it is present in the visible filter. */
public final class GuiSelection {
    private GuiSelection() { }

    /**
     * Preserve a visible selected module; otherwise select the first visible
     * entry, or null if no results remain. Never retain an off-filter module
     * whose settings/keybind could otherwise be edited invisibly.
     */
    private static int indexOf(String id,List<ModuleRegistry.Entry> visible) {
        if(visible==null)throw new IllegalArgumentException("visible modules");
        int selected=-1;
        for(int i=0;i<visible.size();i++) {
            ModuleRegistry.Entry entry=visible.get(i);
            if(entry==null)throw new IllegalArgumentException("null entry");
            if(id!=null && id.equals(entry.module().id()))selected=i;
        }
        return selected;
    }
    public static String reconcile(String current, List<ModuleRegistry.Entry> visible) {
        if(indexOf(current,visible)>=0)return current;
        if (visible.isEmpty()) return null;
        return visible.get(0).module().id();
    }
    /**
     * Move one entry in the currently filtered module list. Direction is
     * strictly +/-1. Stale selections are resolved to the nearest boundary,
     * not to a module hidden by the search/category filter. No wrap-around.
     */
    public static String step(String selected,List<ModuleRegistry.Entry> visible,int direction) {
        if(direction!=1 && direction!=-1)
            throw new IllegalArgumentException("navigation direction");
        int at=indexOf(selected,visible);
        if(visible.isEmpty())return null;
        if(at<0)at=direction>0?0:visible.size()-1;
        else at=Math.max(0,Math.min(visible.size()-1,at+direction));
        return visible.get(at).module().id();
    }
    /**
     * Reveal the selected row in a bounded viewport without moving the row
     * when it is already visible. Manual mouse-wheel scrolling is unaffected:
     * callers invoke this only on a keyboard or filter selection change.
     */
    public static int reveal(String selected,List<ModuleRegistry.Entry> visible,
                             int first,int rows) {
        if(rows<1)throw new IllegalArgumentException("visible rows");
        int at=indexOf(selected,visible);
        int lastStart=Math.max(0,visible.size()-rows);
        int bounded=Math.max(0,Math.min(first,lastStart));
        if(at<0)return bounded;
        if(at<bounded)return at;
        if(at>=bounded+rows)return Math.min(lastStart,at-rows+1);
        return bounded;
    }
}
