package dev.ghostfusion.ui;

/**
 * Original Slate Target HUD miniature for the F7 editor. It represents the
 * player's actual health/distance visibility settings using the same
 * 160x43 or 160x29 composition as the native Slate Target HUD.
 *
 * No pretend opponent, HP percentage or distance reading is generated.
 * All rectangles are clipped to the editor's existing widget footprint.
 * This class has no game, GL, input or persisted-state dependencies.
 */
public final class TargetHudSlateMiniatureGeometry {
    private TargetHudSlateMiniatureGeometry() { }
    private static final int CARD_WIDTH=160;
    private static final int FULL_HEIGHT=43;
    private static final int COMPACT_HEIGHT=29;

    private static void emit(HudPreviewGeometry.Rect bounds,
                             int cardHeight,HudPreviewArt.Sink sink,
                             int x0,int y0,int x1,int y1,int role) {
        if(x0<0||y0<0||x1>CARD_WIDTH||y1>cardHeight||
           x1<=x0||y1<=y0)
            throw new IllegalArgumentException("Slate Target HUD miniature box");
        // Floor the leading edge and ceil the trailing edge so thin
        // one-pixel native details remain visible in tiny F7 thumbnails.
        int left=(int)((long)x0*bounds.width/CARD_WIDTH);
        int top=(int)((long)y0*bounds.height/cardHeight);
        int right=(int)(((long)x1*bounds.width+CARD_WIDTH-1)/CARD_WIDTH);
        int bottom=(int)(((long)y1*bounds.height+cardHeight-1)/cardHeight);
        if(right<=left||bottom<=top)return;
        sink.box(bounds.x+left,bounds.y+top,right-left,bottom-top,role);
    }

    public static void paint(HudPreviewGeometry.Rect bounds,
                             boolean showHealth,boolean showDistance,
                             HudPreviewArt.Sink sink) {
        if(bounds==null||sink==null||bounds.width<1||bounds.height<1)
            throw new IllegalArgumentException("Slate Target HUD miniature");
        int cardHeight=showHealth?FULL_HEIGHT:COMPACT_HEIGHT;
        emit(bounds,cardHeight,sink,0,0,160,cardHeight,HudPreviewArt.PANEL);
        emit(bounds,cardHeight,sink,0,0,2,cardHeight,HudPreviewArt.PRIMARY);
        emit(bounds,cardHeight,sink,8,3,34,4,HudPreviewArt.SECONDARY);
        // Text bars express hierarchy, not a made-up target name.
        emit(bounds,cardHeight,sink,9,8,85,11,HudPreviewArt.SECONDARY);
        if(showHealth) {
            // A neutral unfilled track: never suggest synthetic 70% HP.
            emit(bounds,cardHeight,sink,130,8,151,10,HudPreviewArt.MUTED);
            emit(bounds,cardHeight,sink,9,18,151,19,HudPreviewArt.MUTED);
            emit(bounds,cardHeight,sink,9,25,151,30,HudPreviewArt.TRACK);
        }
        if(showDistance) {
            int y=showHealth?33:18;
            emit(bounds,cardHeight,sink,9,y,40,y+3,HudPreviewArt.MUTED);
            emit(bounds,cardHeight,sink,121,y,151,y+3,
                HudPreviewArt.MUTED);
        }
    }
}
