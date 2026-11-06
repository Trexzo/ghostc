package dev.ghostfusion.ui;

import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * Pure Java, Minecraft-independent geometry for the optional enabled-module HUD.
 * Uses the active font's pixel measurements; all coordinates are viewport-clipped.
 * Disabled/faulted modules are never advertised as active.
 */
public final class ModuleListLayout {
    public static final int MAX_ROWS = 24;
    private static final int MARGIN = 4, TEXT_PAD = 6, ROW_GAP = 4;
    private ModuleListLayout() { }

    public static final class Row {
        public final String label,id;
        public final int x,y,width,height,textX,textY,textWidth;
        private Row(String label,String id,int x,int y,int width,int height,
                    int textX,int textY,int textWidth) {
            this.label=label;this.id=id;this.x=x;this.y=y;this.width=width;this.height=height;
            this.textX=textX;this.textY=textY;this.textWidth=textWidth;
        }
    }
    public static final class Frame {
        public final List<Row> rows;
        public final int x,y,width,height;
        private Frame(List<Row> rows,int x,int y,int width,int height) {
            this.rows=Collections.unmodifiableList(new ArrayList<Row>(rows));
            this.x=x;this.y=y;this.width=width;this.height=height;
        }
    }
    private static final class Candidate {
        final String label,id;
        final int width;
        Candidate(String label,String id,int width) {
            this.label=label;this.id=id;this.width=width;
        }
    }
    private static final Comparator<Candidate> ALPHABETICAL = new Comparator<Candidate>() {
        public int compare(Candidate a,Candidate b) {
            int byName=a.label.compareToIgnoreCase(b.label);
            return byName!=0?byName:a.id.compareTo(b.id);
        }
    };
    private static final Comparator<Candidate> WIDEST_FIRST = new Comparator<Candidate>() {
        public int compare(Candidate a,Candidate b) {
            int byWidth=Integer.compare(b.width,a.width);
            return byWidth!=0?byWidth:ALPHABETICAL.compare(a,b);
        }
    };
    private static Frame empty() {
        return new Frame(Collections.<Row>emptyList(),0,0,0,0);
    }

    /**
     * @param position top_right, top_left, bottom_right, bottom_left
     * @param ordering width or alphabetical
     * @param showRender include RENDER category modules, except the list itself
     */
    public static Frame calculate(ModuleRegistry registry,int viewportW,int viewportH,
                                  int fontH,int maxRows,boolean showRender,
                                  String position,String ordering,
                                  ToIntFunction<String> fontWidth) {
        return calculate(registry,viewportW,viewportH,fontH,maxRows,showRender,
            position,ordering,fontWidth,false,1.0,0.0);
    }
    /** Compact option reduces vertical gaps, without changing existing callers. */
    public static Frame calculate(ModuleRegistry registry,int viewportW,int viewportH,
                                  int fontH,int maxRows,boolean showRender,
                                  String position,String ordering,
                                  ToIntFunction<String> fontWidth,boolean compact) {
        return calculate(registry,viewportW,viewportH,fontH,maxRows,showRender,
            position,ordering,fontWidth,compact,1.0,0.0);
    }
    /** Custom anchors use the same normalized persistence as all other HUD widgets. */
    public static Frame calculate(ModuleRegistry registry,int viewportW,int viewportH,
                                  int fontH,int maxRows,boolean showRender,
                                  String position,String ordering,
                                  ToIntFunction<String> fontWidth,boolean compact,
                                  double anchorX,double anchorY) {
        if(!Double.isFinite(anchorX)||!Double.isFinite(anchorY)||
           anchorX<0||anchorX>1||anchorY<0||anchorY>1)
            throw new IllegalArgumentException("Module List custom anchor");
        if(registry==null || fontWidth==null || viewportW<1 || viewportH<1 ||
           fontH<1 || maxRows<1 || maxRows>MAX_ROWS ||
           position==null || ordering==null)
            throw new IllegalArgumentException("Module List inputs");
        boolean right=position.endsWith("_right"),left=position.endsWith("_left");
        boolean top=position.startsWith("top_"),bottom=position.startsWith("bottom_");
        if(!"custom".equals(position) && ((!left&&!right) || (!top&&!bottom) ||
           !(position.equals("top_right") || position.equals("top_left") ||
             position.equals("bottom_right") || position.equals("bottom_left"))))
            throw new IllegalArgumentException("Module List position");
        if(!ordering.equals("width")&&!ordering.equals("alphabetical"))
            throw new IllegalArgumentException("Module List sort");
        // Height/width admission occurs BEFORE reading registry or measuring font.
        final int rowH=fontH+(compact?2:ROW_GAP);
        final int visibleByHeight=(viewportH-2*MARGIN)/rowH;
        final int textBudget=viewportW-2*MARGIN-2*TEXT_PAD;
        if(visibleByHeight<=0 || textBudget<=0)return empty();
        final int limit=Math.min(maxRows,visibleByHeight);
        List<Candidate> candidates=new ArrayList<Candidate>();
        registry.assertOwnerThread();
        for(ModuleRegistry.Entry entry:registry.all()) {
            if(entry.state()!=ModuleRegistry.State.ENABLED)continue;
            if(entry.module().id().equals("hud.modulelist"))continue;
            if(!showRender && entry.module().category()==GhostModule.Category.RENDER)
                continue;
            String fitted=HudTextFit.fit(entry.module().displayName(),textBudget,fontWidth);
            if(fitted.isEmpty())continue;
            int measured=fontWidth.applyAsInt(fitted);
            if(measured<0 || measured>textBudget)
                throw new IllegalArgumentException("Invalid module font width");
            candidates.add(new Candidate(fitted,entry.module().id(),measured));
        }
        if(candidates.isEmpty())return empty();
        Collections.sort(candidates,ordering.equals("width")?WIDEST_FIRST:ALPHABETICAL);
        final int count=Math.min(limit,candidates.size());
        int measuredW=0;
        for(int i=0;i<count;i++)measuredW=Math.max(measuredW,candidates.get(i).width);
        final int panelW=measuredW+2*TEXT_PAD;
        final int panelH=count*rowH;
        final int x="custom".equals(position)
            ?(int)Math.round(anchorX*Math.max(0,viewportW-panelW))
            :(right?viewportW-MARGIN-panelW:MARGIN);
        final int y="custom".equals(position)
            ?(int)Math.round(anchorY*Math.max(0,viewportH-panelH))
            :(bottom?viewportH-MARGIN-panelH:MARGIN);
        List<Row> rows=new ArrayList<Row>(count);
        for(int i=0;i<count;i++) {
            int rowY=y+i*rowH;
            rows.add(new Row(candidates.get(i).label,candidates.get(i).id,
                x,rowY,panelW,rowH,x+TEXT_PAD,rowY+(rowH-fontH)/2,candidates.get(i).width));
        }
        return new Frame(rows,x,y,panelW,panelH);
    }
}
