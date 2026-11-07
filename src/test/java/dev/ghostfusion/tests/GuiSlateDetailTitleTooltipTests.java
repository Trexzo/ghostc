package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSlateDetailTitleTooltip;
import java.util.Random;

public final class GuiSlateDetailTitleTooltipTests {
    private static long checks;
    private static void ok(boolean b,String note) {
        checks++;if(!b)throw new AssertionError(note);
    }
    private static void invalid(Runnable r,String note) {
        boolean caught=false;
        try{r.run();}catch(IllegalArgumentException expected){caught=true;}
        ok(caught,note);
    }
    public static void main(String[] args) {
        Random rng=new Random(30102026L);
        for(int i=0;i<50000;i++) {
            int x=rng.nextInt(2100)-100,y=rng.nextInt(1500)-100;
            int width=rng.nextInt(400),height=rng.nextInt(17);
            int mx=x+rng.nextInt(500)-30,my=y+rng.nextInt(40)-10;
            boolean clipped=rng.nextBoolean();
            String full="SETTINGS  Very Long Module Name";
            String fitted=clipped?"SETTINGS  Very...":full;
            boolean inside=width>0&&height>0&&mx>=x&&my>=y&&
                (long)mx<(long)x+width&&(long)my<(long)y+height;
            ok(GuiSlateDetailTitleTooltip.reveal(full,fitted,
                x,y,width,height,mx,my)==(clipped&&inside),
                "only hovered shortened titles reveal full registered name");
            ok(!GuiSlateDetailTitleTooltip.reveal(full,full,
                x,y,width,height,mx,my),"full title is never redundant");
        }
        ok(GuiSlateDetailTitleTooltip.reveal("Extra Long","Extra...",
            110,72,90,13,199,84),"last heading pixel admitted");
        ok(!GuiSlateDetailTitleTooltip.reveal("Extra Long","Extra...",
            110,72,90,13,200,84),"status chip gutter excluded");
        ok(!GuiSlateDetailTitleTooltip.reveal("Extra Long","Extra...",
            110,72,90,13,145,85),"breadcrumb row excluded");
        ok(!GuiSlateDetailTitleTooltip.reveal("Full","",
            110,72,90,13,145,78),"missing painted title has no hover");
        invalid(()->GuiSlateDetailTitleTooltip.reveal(null,"Fit",0,0,1,1,0,0),
            "null original rejected");
        invalid(()->GuiSlateDetailTitleTooltip.reveal("Full",null,0,0,1,1,0,0),
            "null fitted rejected");
        invalid(()->GuiSlateDetailTitleTooltip.reveal("Full","Fit",0,0,-1,1,0,0),
            "negative width rejected");
        invalid(()->GuiSlateDetailTitleTooltip.reveal("Full","Fit",0,0,1,-1,0,0),
            "negative height rejected");
        ok(!GuiSlateDetailTitleTooltip.reveal("Full","",
            Integer.MAX_VALUE,0,100,12,Integer.MAX_VALUE,1),
            "extreme coordinates with invisible title do not reveal");
        System.out.println("GUI_SLATE_DETAIL_TOOLTIP_PASS checks="+checks);
    }
}
