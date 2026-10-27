package dev.ghostfusion.forge189;

import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ReservedKeys;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.Setting;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import dev.ghostfusion.ui.UiState;
import dev.ghostfusion.ui.GuiThemePalette;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.GuiNebulaWorkspace;
import dev.ghostfusion.ui.GuiSettingPresentation;
import dev.ghostfusion.ui.GuiScrollIndicator;
import dev.ghostfusion.ui.GuiHitZones;
import dev.ghostfusion.ui.GuiChrome;
import dev.ghostfusion.ui.GuiSelection;
import dev.ghostfusion.ui.GuiFooterText;
import dev.ghostfusion.ui.GuiSettingKeyboard;
import dev.ghostfusion.ui.GuiSearchFocus;
import dev.ghostfusion.ui.GuiKeybindCapture;
import dev.ghostfusion.ui.HudPositions.Widget;
import dev.ghostfusion.ui.HudPreviewGeometry;
import dev.ghostfusion.ui.HudPreviewArt;
import dev.ghostfusion.ui.HudThemeTokens;
import dev.ghostfusion.ui.HudSnapPolicy;
import dev.ghostfusion.ui.HudOverlapPolicy;
import dev.ghostfusion.ui.ModuleListLayout;
import dev.ghostfusion.ui.HudFootprints;
import dev.ghostfusion.ui.HudScaleLayout;
import dev.ghostfusion.ui.HudScaleEditor;
import dev.ghostfusion.ui.HudLayoutPresets;
import dev.ghostfusion.ui.HudLayoutPresets.Preset;
import dev.ghostfusion.ui.HudTextFit;
import dev.ghostfusion.ui.GuiSliderDrag;
import dev.ghostfusion.ui.NumericSliderScale;
import dev.ghostfusion.ui.StatusHudLayout;
import dev.ghostfusion.ui.HudEditorControls;
import dev.ghostfusion.ui.HudDragCapture;
import dev.ghostfusion.ui.HudPreviewGeometry.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Original Minecraft 1.8.9 ClickGUI. F7 opens, ESC closes.
 * Search, category filters, toggle, keybind capture and typed settings
 * are backed by the independent core model; no direct game-state mutation.
 */
public final class GhostClickGui extends GuiScreen {
    private final HostRuntime host;
    private final UiState ui;
    private GuiTextField search;
    private int searchPositionX=-1, searchPositionY=-1, searchPositionWidth=-1;
    private String selected = "hud.status";
    private String capture;
    // Ctrl-alone is a valid bind, but its key-down must not consume a
    // pending capture before the user can complete Ctrl+F search.
    private int pendingControlBind;
    private String message = "";
    private boolean hudPage;
    private boolean hudDragging;
    private int snapGuideX=HudSnapPolicy.NO_GUIDE,snapGuideY=HudSnapPolicy.NO_GUIDE;
    private final HudDragCapture hudCapture = new HudDragCapture();
    private Widget draggingWidget; // null means the original Status HUD
    private final HudEditorControls hudControls = new HudEditorControls();
    private final GuiSliderDrag sliderDrag = new GuiSliderDrag();
    private int dragX, dragY, scroll, settingScroll;
    // -1 = keyboard focus on module list; >=0 selects a setting row.
    private int keyboardSetting=-1;
    private int x, y, w, h, side;
    private int listY, listH, visibleRows;
    private int previewX, previewY, previewW, previewH;
    private static final int HUD_WIDTH = 124;
    private static final int HUD_HEIGHT = 22;
    // One reusable sink paints schematic HUD thumbnails without allocating
    // lambdas, arrays or per-widget scene graphs on each editor frame.
    private HudThemeTokens miniatureTheme=HudThemeTokens.of("classic");
    private final HudPreviewArt.Sink miniaturePainter=new HudPreviewArt.Sink(){
        public void box(int px,int py,int pw,int ph,int role) {
            int color=role==HudPreviewArt.PANEL?miniatureTheme.panel:
                role==HudPreviewArt.PRIMARY?miniatureTheme.accent:
                role==HudPreviewArt.SECONDARY?miniatureTheme.secondary:
                role==HudPreviewArt.TRACK?miniatureTheme.track:
                miniatureTheme.muted;
            rect(px,py,pw,ph,color);
        }
    };
    private ModuleListLayout.Frame moduleListFrameCache;
    private static final Preset[] HUD_PRESETS=Preset.values();
    // Reuse the active Minecraft font when measuring GUI labels, without
    // allocating a new callback on each rendered row or each frame.
    private final java.util.function.ToIntFunction<String> guiFontWidths =
        new java.util.function.ToIntFunction<String>() {
            public int applyAsInt(String text) {
                return fontRendererObj.getStringWidth(text);
            }
        };

    public GhostClickGui(HostRuntime host) {
        if (host == null) throw new IllegalArgumentException("host");
        this.host = host; ui = host.ui();
    }
    @Override public void initGui() {
        moduleListFrameCache=null;
        Keyboard.enableRepeatEvents(true);
        rebuild();
        host.inputFocus(true);
    }
    private void rebuild() {
        GuiLayout g = GuiLayout.calculate(width,height,ui.layout());
        if(sliderDrag.active() &&
           (!sliderDrag.sameViewport(width,height) || !GuiChrome.interactive(g)))
            finishSliderDrag();
        x=g.x; y=g.y; w=g.width; h=g.height; side=g.sidebar;
        listY=g.listY; listH=g.listHeight; visibleRows=g.visibleRows;
        GuiNebulaWorkspace workspace=nebulaWorkspace(g);
        if(workspace!=null)visibleRows=workspace.moduleRows;
        previewX=g.previewX; previewY=g.previewY;
        previewW=g.previewWidth; previewH=g.previewHeight;
        if(hudDragging && (!hudCapture.sameViewport(width,height) ||
                           !GuiChrome.interactive(g)))finishHudDrag();
        if (!GuiChrome.interactive(g)) {
            hudDragging=false;
            draggingWidget=null;
            capture=null;
            pendingControlBind=0;
        }
        syncSearch(g);
    }
    private void syncSearch(GuiLayout g) {
        GuiNebulaWorkspace pane=nebulaWorkspace(g);
        int targetX=pane==null?g.searchX:pane.searchX();
        int targetY=pane==null?g.searchY:pane.searchY();
        int targetWidth=pane==null?g.searchWidth:pane.searchWidth();
        if (search!=null && searchPositionX==targetX &&
            searchPositionY==targetY && searchPositionWidth==targetWidth) return;
        String previous=search==null?ui.query():search.getText();
        boolean focused=search!=null && search.isFocused();
        GuiTextField resized=new GuiTextField(42,fontRendererObj,
            targetX,targetY,targetWidth,17);
        resized.setMaxStringLength(100);
        resized.setText(previous);
        resized.setFocused(focused);
        search=resized;
        searchPositionX=targetX; searchPositionY=targetY;
        searchPositionWidth=targetWidth;
    }
    private GuiThemePalette palette() { return GuiThemePalette.of(ui.theme()); }
    private GuiNebulaWorkspace nebulaWorkspace(GuiLayout zone) {
        return GuiNebulaWorkspace.of(zone,ui);
    }
    private int settingsRows(GuiLayout zone) {
        GuiNebulaWorkspace workspace=nebulaWorkspace(zone);
        return workspace==null?GuiHitZones.visibleSettings(zone):workspace.settingRows;
    }
    private void nebulaSwitch(int x,int y,boolean enabled) {
        int track=enabled?0xFF829FCD:0xFF343A49;
        // Three nested spans approximate small rounded switch ends.
        rect(x+2,y,23,13,track);
        rect(x,y+2,27,9,track);
        rect(x+(enabled?15:3),y+2,10,9,0xFFF0F3FA);
    }
    /** Dedicated Rise-style modules + settings panes, with native GUI input parity. */
    private void drawNebulaModules(GuiNebulaWorkspace pane,int mx,int my) {
        drawNebulaSidebar(mx,my);
        if(search!=null) {
            search.drawTextBox();
            if(search.getText().isEmpty()&&!search.isFocused())
                label("Search modules...",pane.searchX()+5,pane.searchY()+5,
                    palette().muted);
        }
        GuiThemePalette color=palette();
        rect(pane.detailsX-6,pane.detailsY,1,pane.detailsHeight,color.divider);
        // The count uses the same filtered registry view as click/keyboard paths.
        List<ModuleRegistry.Entry> filtered=entries();
        label("MODULES  "+filtered.size(),pane.modulesX+3,pane.detailsY+3,color.muted);
        scroll=Math.max(0,Math.min(scroll,Math.max(0,filtered.size()-pane.moduleRows)));
        for(int i=scroll;i<filtered.size()&&i<scroll+pane.moduleRows;i++) {
            ModuleRegistry.Entry entry=filtered.get(i);
            int yy=pane.modulesY+(i-scroll)*29;
            boolean active=entry.module().id().equals(selected);
            boolean hovered=inside(mx,my,pane.modulesX,yy,pane.modulesWidth,26);
            if(active||hovered)
                rect(pane.modulesX,yy,pane.modulesWidth,26,
                    active?0x58405266:0x242E3746);
            rect(pane.modulesX+6,yy+25,pane.modulesWidth-12,1,0x304D5669);
            if(active)rect(pane.modulesX+1,yy+9,2,8,color.accent);
            label(trim(entry.module().displayName(),pane.modulesWidth-61),
                pane.modulesX+10,yy+8,active?color.text:color.muted);
            boolean enabled=entry.state()==ModuleRegistry.State.ENABLED;
            boolean faulted=entry.state()==ModuleRegistry.State.FAULTED;
            if(faulted) {
                rect(pane.modulesX+pane.modulesWidth-43,yy+5,38,16,0x665A343F);
                label("RESET",pane.modulesX+pane.modulesWidth-40,yy+8,0xFFEBA4AF);
            } else nebulaSwitch(pane.modulesX+pane.modulesWidth-35,yy+6,enabled);
        }
        drawNebulaScrollIndicator(pane.modulesX+pane.modulesWidth+2,
            pane.modulesY,pane.moduleRows*29,pane.moduleRows,filtered.size(),scroll);
        label("CONFIGURATION",pane.detailsX+9,pane.detailsY+3,color.muted);
        // No eagerly-created description list or external font render path.
        if(selected==null) {
            keyboardSetting=-1;
            label("No matching modules",pane.detailsX+9,pane.detailsY+28,color.muted);
            return;
        }
        ModuleRegistry.Entry entry;
        try {entry=host.modules().get(selected);}
        catch(IllegalArgumentException ignored){return;}
        label(trim(entry.module().displayName(),pane.detailsWidth-22),
            pane.detailsX+9,pane.detailsY+23,color.text);
        rect(pane.detailsX+9,pane.detailsY+44,pane.detailsWidth-18,1,color.divider);
        rect(pane.detailsX+9,pane.bindingY,pane.detailsWidth-18,24,0x45333C4E);
        label("KEYBIND",pane.detailsX+16,pane.bindingY+8,color.muted);
        String bind=capture!=null&&capture.equals(selected)?"PRESS KEY":
            entry.keybind()==0?"NONE":Keyboard.getKeyName(entry.keybind());
        label(trim(bind,92),pane.detailsX+pane.detailsWidth-104,
            pane.bindingY+8,color.accent);
        label("SETTINGS",pane.detailsX+9,pane.settingY-14,color.muted);
        List<Map.Entry<String,Setting<?>>> settings=
            new ArrayList<Map.Entry<String,Setting<?>>>(entry.settings().entrySet());
        settingScroll=Math.max(0,Math.min(settingScroll,
            Math.max(0,settings.size()-pane.settingRows)));
        keyboardSetting=GuiSettingKeyboard.visibleFocus(
            keyboardSetting,settingScroll,settings.size(),pane.settingRows);
        for(int i=settingScroll;i<settings.size()&&i<settingScroll+pane.settingRows;i++){
            Map.Entry<String,Setting<?>> row=settings.get(i);
            Setting<?> setting=row.getValue();
            int yy=pane.settingY+(i-settingScroll)*30;
            if(i==keyboardSetting)
                rect(pane.detailsX+8,yy,pane.detailsWidth-16,27,0x50465568);
            rect(pane.detailsX+9,yy+26,pane.detailsWidth-18,1,0x263E4558);
            int kind=setting instanceof Setting.Bool?0:
                setting instanceof Setting.Number?1:2;
            label(trim(GuiSettingPresentation.label(row.getKey()),
                pane.settingLabelBudget(kind)),pane.detailsX+14,yy+9,color.text);
            if(setting instanceof Setting.Bool) {
                nebulaSwitch(pane.detailsX+pane.detailsWidth-43,yy+7,
                    ((Setting.Bool)setting).get());
            } else if(setting instanceof Setting.Number) {
                Setting.Number value=(Setting.Number)setting;
                double frac=NumericSliderScale.fraction(value.get(),value.min(),value.max());
                int bx=pane.sliderX(),bw=pane.sliderWidth();
                rect(bx,yy+13,bw,3,color.barTrack);
                rect(bx,yy+13,(int)Math.round(bw*frac),3,color.accent);
                label(trim(GuiSettingPresentation.number(row.getKey(),value.get()),
                    pane.sliderWidth()),bx,yy+2,color.muted);
            } else {
                String text=trim(GuiSettingPresentation.choice(setting.serialize()),
                    pane.choiceValueWidth());
                label(text,pane.detailsX+pane.detailsWidth-16-
                    fontRendererObj.getStringWidth(text),yy+9,color.accent);
            }
        }
        drawNebulaScrollIndicator(pane.detailsX+pane.detailsWidth-5,
            pane.settingY,pane.settingRows*30,
            pane.settingRows,settings.size(),settingScroll);
        if(settings.size()>pane.settingRows)
            label((settingScroll+1)+"-"+Math.min(settings.size(),settingScroll+
                pane.settingRows)+" / "+settings.size(),
                pane.detailsX+10,y+h-20,color.muted);
    }
    private int bg() { return palette().background; }
    private int panel() { return palette().panel; }
    private int accent() { return palette().accent; }
    private static boolean inside(int mx, int my, int px, int py, int pw, int ph) {
        return mx >= px && my >= py && mx < px + pw && my < py + ph;
    }
    private void rect(int rx, int ry, int rw, int rh, int color) {
        if (rw > 0 && rh > 0) drawRect(rx, ry, rx + rw, ry + rh, color);
    }
    private void label(String value, int atX, int atY, int color) {
        fontRendererObj.drawStringWithShadow(value, atX, atY, color);
    }
    @Override public void drawScreen(final int mx, final int my, float partialTicks) {
        host.diagnostics().record(Counter.GUI_FRAME);
        rebuild();
        if (search != null && !hudPage) {
            search.updateCursorCounter();
            ui.query(search.getText());
        }
        RenderStateGuard.draw(new Runnable() {
            public void run() { drawUi(mx, my); }
        });
    }
    private void drawUi(int mx, int my) {
        // Cache the live font-measured Module List once per GUI frame.
        moduleListFrameCache=null;
        rect(0, 0, width, height, 0x99060B13);
        rect(x, y, w, h, bg());
        if (!GuiChrome.interactive(GuiLayout.calculate(width,height,ui.layout()))) {
            label(trim("GhostC: enlarge game window or reduce GUI scale",w-16),
                x+8,y+Math.min(18,Math.max(0,h-1)),accent());
            return;
        }
        final GuiThemePalette theme=palette();
        rect(x, y, side, h, panel());
        rect(x, y, w, 30, theme.header);
        if(theme.nebula)drawNebulaChrome(theme);
        label("GHOSTC", x + 10, y + 10, accent());
        int menuX = x + side + 8;
        GuiNebulaWorkspace modern=nebulaWorkspace(
            GuiLayout.calculate(width,height,ui.layout()));
        if(modern!=null) {
            label("Modules",menuX,y+11,hudPage?theme.muted:theme.text);
            label("HUD",menuX+76,y+11,hudPage?theme.text:theme.muted);
            rect(menuX+(hudPage?76:0),y+28,hudPage?23:42,2,theme.accent);
        } else {
            label(hudPage ? "MODULES" : "[MODULES]", menuX, y + 11, hudPage ? 0xFFB5B6C1 : 0xFFFFFFFF);
            label(hudPage ? "[HUD]" : "HUD", menuX + 76, y + 11, hudPage ? 0xFFFFFFFF : 0xFFB5B6C1);
        }
        rect(x + w - 99, y + 6, 40, 17, panel());
        rect(x + w - 55, y + 6, 50, 17, panel());
        label(ui.layout() == UiState.Layout.WIDE ? "WIDE" : "SMALL", x + w - 96, y + 10, 0xFFF2F3FF);
        label(theme.label, x + w - 52, y + 10, accent());
        if (hudPage) drawHudEditor(mx, my);
        else drawModules(mx, my);
        // Errors own the footer and must never draw beyond the content panel.
        // trim() applies the shared bounded Unicode/font-width fit policy.
        String footerWarning=GuiFooterText.warning(message,host.uiWarning());
        if (!footerWarning.isEmpty())
            label(trim(footerWarning,GuiFooterText.pixelBudget(w,side)),
                x + side + 11,y + h - 13,0xFFFFB3A6);
    }
    /** A deliberate, low-cost Nebula surface treatment; no new hit zones. */
    private void drawNebulaChrome(GuiThemePalette theme) {
        // Subtle border and section separator establish hierarchy without glow.
        rect(x,y,w,1,theme.frame);
        rect(x,y+h-1,w,1,theme.frame);
        rect(x,y,1,h,theme.frame);
        rect(x+w-1,y,1,h,theme.frame);
        rect(x+side-1,y+30,1,Math.max(0,h-31),theme.frame);
        rect(x+side+1,y+29,Math.max(0,w-side-2),1,theme.divider);
        // Short twin aurora lines echo the Nebula in-game Target HUD.
        int segment=Math.max(0,Math.min(44,w-side-30));
        rect(x+side+9,y+29,segment,1,theme.accent);
        rect(x+side+9+segment,y+29,Math.min(20,Math.max(0,w-side-30-segment)),
            1,theme.secondary);
        rect(x+5,y+9,2,12,theme.accent);
        rect(x+5,y+21,2,2,theme.secondary);
        // Distinguish title, controls, and content without texture allocations.
        rect(x+w-108,y+3,Math.min(100,w-109),1,theme.sheen);
    }
    /** Full-height navigation styled for the new Nebula wide workspace. */
    private void drawNebulaSidebar(int mx,int my) {
        GuiThemePalette theme=palette();
        label("NAVIGATION",x+10,y+49,theme.muted);
        String[] captions={"All","Combat","Movement","World","Render","Utility"};
        GhostModule.Category[] categories=GhostModule.Category.values();
        for(int i=0;i<captions.length;i++){
            GhostModule.Category category=i==0?null:categories[i-1];
            boolean active=ui.category()==category;
            int yy=y+62+i*23;
            boolean hover=inside(mx,my,x+4,yy,side-8,19);
            if(active||hover)
                rect(x+4,yy,side-8,19,active?0x45485C74:0x222F3D4C);
            if(active)rect(x+4,yy+5,2,9,theme.accent);
            label(captions[i],x+13,yy+6,active?theme.text:theme.muted);
        }
        rect(x+12,y+h-53,side-24,1,theme.divider);
        label("CTRL+F  SEARCH",x+11,y+h-44,theme.muted);
        label("F7  CLOSE",x+11,y+h-29,theme.muted);
        label("ESC  CLOSE",x+11,y+h-16,theme.muted);
    }
    private void drawNebulaScrollIndicator(int atX,int atY,int height,
                                            int visible,int total,int first) {
        int thumb=GuiScrollIndicator.height(height,total,visible);
        if(thumb<=0)return;
        rect(atX,atY,2,height,0x24343D4D);
        rect(atX,atY+GuiScrollIndicator.offset(height,total,visible,first),
            2,thumb,palette().accent);
    }
    private void drawSidebar() {
        int yy = y + 47;
        label("FILTER", x + 10, yy, 0xFF979DAF);
        yy += 18;
        String[] names = {"ALL", "COMBAT", "MOVEMENT", "WORLD", "RENDER", "UTILITY"};
        for (int i = 0; i < names.length; i++) {
            GhostModule.Category c = i == 0 ? null : GhostModule.Category.valueOf(names[i]);
            boolean active = ui.category() == c;
            if (active) {
                rect(x + 4, yy - 3, side - 8, 19,
                    palette().nebula?palette().selectedCategory:0x556B83EA);
                if(palette().nebula)rect(x+4,yy-3,2,19,palette().accent);
            }
            if (yy < y + h - 48) label(names[i], x + 12, yy + 1,
                active ? accent() : 0xFFD4D7E0);
            yy += 23;
        }
        label("UP/DOWN", x + 10, y + h - 48, 0xFF929BAC);
        label("F7: CLOSE", x + 10, y + h - 35, 0xFF929BAC);
        label("ESC: CLOSE", x + 10, y + h - 23, 0xFF929BAC);
    }
    private List<ModuleRegistry.Entry> entries() {
        List<ModuleRegistry.Entry> filtered=ui.filtered(host.modules());
        String next=GuiSelection.reconcile(selected,filtered);
        if (selected == null ? next != null : !selected.equals(next)) {
            finishSliderDrag();
            selected=next;
            settingScroll=0;
            keyboardSetting=-1;
            capture=null;
            pendingControlBind=0;
            scroll=GuiSelection.reveal(selected,filtered,scroll,Math.max(1,visibleRows));
        }
        return filtered;
    }
    private void drawModules(int mx, int my) {
        GuiNebulaWorkspace workspace=nebulaWorkspace(
            GuiLayout.calculate(width,height,ui.layout()));
        if(workspace!=null) {drawNebulaModules(workspace,mx,my);return;}
        drawSidebar();
        if (search != null) search.drawTextBox();
        List<ModuleRegistry.Entry> filtered = entries();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, filtered.size() - visibleRows)));
        int end = Math.min(filtered.size(), scroll + visibleRows);
        for (int i = scroll; i < end; i++) {
            ModuleRegistry.Entry e = filtered.get(i);
            int ry = listY + (i - scroll) * 28;
            if (ry + 27 > listY + listH) break;
            boolean focused = e.module().id().equals(selected);
            GuiThemePalette style=palette();
            rect(x + side + 9, ry, w - side - 20, 25,
                style.nebula?(focused?style.selectedModule:style.idleModule)
                            :(focused?0x664B597D:0x442D344A));
            if(style.nebula && focused)
                rect(x+side+9,ry,2,25,style.accent);
            label(trim(e.module().displayName(), Math.max(60, w - side - 97)),
                    x + side + 15, ry + 8, focused ? 0xFFFFFFFF : 0xFFE4E7F0);
            boolean enabled = e.state() == ModuleRegistry.State.ENABLED;
            boolean faulted = e.state() == ModuleRegistry.State.FAULTED;
            int right = x + w - 54;
            rect(right, ry + 5, 39, 15,
                faulted ? 0xFFAA5360 : enabled ? accent() : 0xFF586073);
            // RESET is a distinct safe action, not another enable attempt.
            label(faulted ? "RESET" : enabled ? "ON" : "OFF",
                right + (faulted ? 5 : 9), ry + 8,
                enabled && !faulted ? 0xFF101320 : 0xFFFFFFFF);
        }
        int sy = listY + listH + 5;
        rect(x + side + 9, sy - 4, w - side - 20, 1, palette().divider);
        if (selected == null) {
            keyboardSetting=-1;
            label("No matching modules", x + side + 13, sy + 5, 0xFFBAC1D3);
            return;
        }
        ModuleRegistry.Entry e;
        try { e = host.modules().get(selected); }
        catch (IllegalArgumentException ex) { return; }
        if(e.state()==ModuleRegistry.State.FAULTED)
            label(trim("FAULT: "+e.lastFault()+"  (RESET to clear)",
                Math.max(0,w-side-39)),x+side+13,sy+4,0xFFFFB3A6);
        else
            label("SETTINGS  " + e.module().displayName(), x + side + 13, sy + 4, accent());
        int bindY = sy + 22;
        rect(x + side + 11, bindY, w - side - 26, 18,
            palette().nebula?palette().idleSetting:0x66394359);
        String keyText = capture != null && capture.equals(selected) ? "Press key (ESC cancel / BACKSPACE clear)" :
            "KEYBIND [B]: " + (e.keybind() == 0 ? "NONE" : Keyboard.getKeyName(e.keybind()));
        label(trim(keyText, w - side - 39), x + side + 16, bindY + 5, 0xFFEAECF5);
        int sY = bindY + 24;
        List<Map.Entry<String, Setting<?>>> settings = new ArrayList<Map.Entry<String, Setting<?>>>(e.settings().entrySet());
        int available = Math.max(0, (y + h - 18 - sY) / 22);
        settingScroll = Math.max(0, Math.min(settingScroll, Math.max(0, settings.size() - available)));
        keyboardSetting=GuiSettingKeyboard.visibleFocus(
            keyboardSetting,settingScroll,settings.size(),available);
        for (int i = settingScroll; i < settings.size() && i < settingScroll + available; i++) {
            Map.Entry<String, Setting<?>> kv = settings.get(i);
            Setting<?> setting = kv.getValue();
            GuiThemePalette settingsTheme=palette();
            rect(x + side + 11, sY, w - side - 26, 19,
                settingsTheme.nebula?(i==keyboardSetting
                    ?settingsTheme.selectedSetting:settingsTheme.idleSetting)
                    :(i==keyboardSetting ? 0x886A588D : 0x443B465C));
            label(trim(setting.id(), Math.max(30, (w - side) / 2 - 14)), x + side + 15, sY + 5, 0xFFE0E4EF);
            int right = x + w - 17;
            if (setting instanceof Setting.Number) {
                Setting.Number number = (Setting.Number) setting;
                double fraction = NumericSliderScale.fraction(
                    number.get(),number.min(),number.max());
                int barWidth = Math.max(33, (w - side) / 3);
                rect(right - barWidth, sY + 7, barWidth, 6, palette().barTrack);
                rect(right - barWidth, sY + 7, (int) (barWidth * fraction), 6, accent());
                label(String.format(java.util.Locale.ROOT, "%.2f", number.get()), right - barWidth - 37, sY + 5, 0xFFC6CBD7);
            } else {
                label(trim(setting.serialize(), Math.max(30, (w - side) / 3)),
                    x + w - 17 - Math.min((w - side) / 3, 90), sY + 5, accent());
            }
            sY += 22;
        }
        // Footer has an 18px reserved strip below visible settings; never
        // paint keyboard help over the keybind row. Warnings own this footer.
        String footer = null;
        if (keyboardSetting>=0 && available>0 && keyboardSetting<settings.size())
            footer = GuiSettingKeyboard.hint(settings.get(keyboardSetting).getValue());
        else if (settings.size() > available)
            footer = (settingScroll + 1) + "-" +
                Math.min(settings.size(), settingScroll + available) +
                "/" + settings.size() + "  scroll";
        if (footer!=null && GuiFooterText.warning(message,host.uiWarning()).isEmpty())
            label(trim(footer,GuiFooterText.pixelBudget(w,side)),
                x+side+13,y+h-13,0xFFE7E8F5);
    }
    private String trim(String text, int maxPixels) {
        // The legacy substring loop could split a supplementary Unicode
        // scalar and repeatedly scan large labels on each ClickGUI frame.
        // R2.31's shared sanitizer/fit policy bounds scans and measures the
        // current Minecraft font without splitting surrogate pairs.
        return HudTextFit.fit(text,Math.max(0,maxPixels),guiFontWidths);
    }
    private HudPreviewGeometry previewGeometry() {
        return new HudPreviewGeometry(previewX,previewY,previewW,previewH,width,height);
    }
    /** Use the same enabled module names/font metrics as the actual in-game list. */
    private ModuleListLayout.Frame listFootprint() {
        if(moduleListFrameCache!=null)return moduleListFrameCache;
        String position=host.moduleListPosition();
        // Custom coordinates affect only placement, not a single pixel of size.
        double scale=host.moduleListScale();
        int virtualW=scale==1.0?width:HudScaleLayout.logicalViewport(width,scale);
        int virtualH=scale==1.0?height:HudScaleLayout.logicalViewport(height,scale);
        moduleListFrameCache=ModuleListLayout.calculate(host.modules(),virtualW,virtualH,
            fontRendererObj.FONT_HEIGHT,host.moduleListMaxRows(),
            host.moduleListShowRender(),"custom".equals(position)?"top_right":position,
            host.moduleListOrder(),guiFontWidths,host.moduleListCompact());
        return moduleListFrameCache;
    }
    private double hudWidgetScale(Widget widget) {
        switch(widget) {
            case KEYSTROKES: return host.keysScale();
            case CPS: return host.cpsScale();
            case TARGET: return host.targetScale();
            case MODULE_LIST: return host.moduleListScale();
            default: throw new IllegalArgumentException("HUD scale widget");
        }
    }
    private int hudWidth(Widget widget) {
        if(widget==Widget.MODULE_LIST) {
            int measured=listFootprint().width;
            return HudScaleLayout.extent(
                measured>0?measured:HudFootprints.width(widget),hudWidgetScale(widget));
        }
        return HudScaleLayout.extent(HudFootprints.width(widget),hudWidgetScale(widget));
    }
    private int hudHeight(Widget widget) {
        if(widget==Widget.MODULE_LIST) {
            int measured=listFootprint().height;
            return HudScaleLayout.extent(
                measured>0?measured:HudFootprints.height(widget,true,true,true),
                hudWidgetScale(widget));
        }
        return HudScaleLayout.extent(
            HudFootprints.height(widget,host.keysJump(),host.keysSneak(),
                host.targetHealth()),hudWidgetScale(widget));
    }
    /** Preset corners are previewed at their real frame position; dragging uses saved anchors. */
    private Rect hudPreviewRect(HudPreviewGeometry geometry,Widget widget) {
        if(widget==Widget.MODULE_LIST &&
           !(hudDragging && draggingWidget==Widget.MODULE_LIST) &&
           !"custom".equals(host.moduleListPosition())) {
            ModuleListLayout.Frame frame=listFootprint();
            int actualW=hudWidth(widget),actualH=hudHeight(widget);
            if(frame.width<=0||frame.height<=0) {
                double xx=host.moduleListPosition().endsWith("_right")?1.0:0.0;
                double yy=host.moduleListPosition().startsWith("bottom_")?1.0:0.0;
                return geometry.at(xx,yy,actualW,actualH);
            }
            double scale=host.moduleListScale();
            int logicalW=scale==1.0?width:HudScaleLayout.logicalViewport(width,scale);
            int logicalH=scale==1.0?height:HudScaleLayout.logicalViewport(height,scale);
            return geometry.at((double)frame.x/Math.max(1,logicalW-frame.width),
                (double)frame.y/Math.max(1,logicalH-frame.height),actualW,actualH);
        }
        return geometry.widget(ui.widgets(),widget,hudWidth(widget),hudHeight(widget));
    }
    /** Make the rendered corner the drag origin without pre-saving a new profile. */
    private void captureModuleListOrigin(HudPreviewGeometry geometry,Rect visible) {
        if("custom".equals(host.moduleListPosition()))return;
        Rect canvas=geometry.canvas;
        ui.widgets().position(Widget.MODULE_LIST,
            (double)(visible.x-canvas.x)/Math.max(1,canvas.width-visible.width),
            (double)(visible.y-canvas.y)/Math.max(1,canvas.height-visible.height));
    }
    private StatusHudLayout statusFootprint() {
        boolean status=host.featureEnabled("hud.status");
        boolean metrics=host.featureEnabled("hud.metrics");
        boolean fps=metrics && host.showFps();
        boolean coordinates=metrics && host.showCoordinates() && mc.thePlayer!=null;
        String pingText=metrics && host.showPing()
            ? ForgePingReadout.read(mc) : null;
        boolean ping=pingText!=null;
        // The Status anchor remains selectable even when both modules are off.
        if(!status && !fps && !coordinates && !ping) status=true;
        String title=host.detailedStatus()
            ? "GhostC "+GhostCMod.CLIENT_VERSION+" | F7 ClickGUI" : "GhostC";
        float scale=host.statusScale();
        int statusW=Math.max(1,Math.round((fontRendererObj.getStringWidth(title)+4)*scale));
        int statusH=Math.max(1,Math.round((fontRendererObj.FONT_HEIGHT+5)*scale));
        String fpsText=fps?"FPS "+Minecraft.getDebugFPS():"";
        String coordsText=coordinates
            ? String.format(java.util.Locale.ROOT,"XYZ %.1f %.1f %.1f",
                mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ) : "";
        return StatusHudLayout.calculate(ui,width,height,status,fps,coordinates,ping,
            statusW,statusH,fontRendererObj.getStringWidth(fpsText),
            fontRendererObj.getStringWidth(coordsText),
            ping?fontRendererObj.getStringWidth(pingText):0,fontRendererObj.FONT_HEIGHT);
    }
    private int statusPreviewWidth() { return statusFootprint().measuredWidth; }
    private int statusPreviewHeight() { return statusFootprint().measuredHeight; }
    /** Match the chosen skin of each on-screen HUD, not the editor's selection. */
    private HudThemeTokens miniatureThemeFor(Widget widget) {
        String appearance;
        if(widget==null) {
            appearance=ui.theme()==UiState.Theme.NEBULA?"nebula":"classic";
        } else {
            switch(widget) {
                case KEYSTROKES: appearance=host.keysAppearance();break;
                case CPS: appearance=host.cpsAppearance();break;
                case TARGET:
                    appearance=host.targetSkin();
                    if("glass".equals(appearance))appearance="classic";
                    break;
                case MODULE_LIST: appearance=host.moduleListAppearance();break;
                default: appearance="classic";
            }
        }
        return HudThemeTokens.of(appearance);
    }
    private void drawHudMiniature(Rect bounds,Widget widget,boolean selected) {
        miniatureTheme=miniatureThemeFor(widget);
        HudPreviewArt.paint(bounds,widget,miniaturePainter);
        if(selected) {
            // Preview-only selection outline. The collision-warning outline wins later.
            rect(bounds.x,bounds.y,bounds.width,1,miniatureTheme.accent);
            rect(bounds.x,bounds.y+bounds.height-1,bounds.width,1,miniatureTheme.accent);
            rect(bounds.x,bounds.y,1,bounds.height,miniatureTheme.accent);
            rect(bounds.x+bounds.width-1,bounds.y,1,bounds.height,miniatureTheme.accent);
        }
    }
    /** F7 preset actions intentionally change HUD anchors only. */
    private void applyHudLayoutPreset(Preset preset) {
        finishHudDrag();
        moduleListFrameCache=null;
        boolean changed=HudLayoutPresets.apply(ui,preset);
        if(!preset.listPosition.equals(host.moduleListPosition())) {
            if("top_right".equals(preset.listPosition))
                host.uiActions().reset("hud.modulelist","position");
            else
                host.uiActions().choiceEdge("hud.modulelist","position",true);
        }
        if(changed)host.persistUi();
        message="";
    }
    private void drawHudPresetSidebar() {
        if(h<320||side<85)return;
        label("LAYOUTS",x+10,y+h-130,palette().muted);
        for(Preset preset:HUD_PRESETS) {
            int top=HudLayoutPresets.buttonY(y,h,preset);
            boolean selected=HudLayoutPresets.matches(ui,preset,host.moduleListPosition());
            rect(x+6,top,side-12,18,
                selected?palette().selectedCategory:palette().idleModule);
            if(selected)rect(x+6,top,2,18,palette().accent);
            label(preset.label,x+13,top+5,
                selected?palette().text:palette().muted);
        }
    }
    private void drawHudEditor(int mx, int my) {
        if (previewW<1 || previewH<1) return;
        label("HUD POSITION EDITOR",x+side+12,y+49,accent());
        drawHudPresetSidebar();
        // Display the scale of the selected widget in a fixed, readable place.
        String editorModule=HudScaleEditor.moduleId(hudControls.selected());
        dev.ghostfusion.core.Setting.Number zoom=
            (dev.ghostfusion.core.Setting.Number)host.modules()
                .get(editorModule).settings().get("scale");
        label(String.format(java.util.Locale.ROOT,"%.2fx",zoom.get()),
            x+w-57,y+49,palette().secondary);
        int tabsX=x+side+12, tabsY=y+62, tabsWidth=w-side-24;
        for (HudEditorControls.Item item:HudEditorControls.Item.values()) {
            int bx=HudEditorControls.tabLeft(tabsX,tabsWidth,item);
            int bw=HudEditorControls.tabWidth(tabsWidth,item);
            rect(bx,tabsY,bw,17,item==hudControls.selected()?0xFF575C8D:panel());
            label(trim(item.label,bw-5),bx+3,tabsY+4,
                item==hudControls.selected()?0xFFFFFFFF:0xFFBEC6D9);
        }
        rect(previewX,previewY,previewW,previewH,0xFF0D1220);
        HudPreviewGeometry geometry=previewGeometry();
        Rect canvas=geometry.canvas;
        rect(canvas.x,canvas.y,canvas.width,canvas.height,0xFF192238);
        for(int gx=canvas.x+30;gx<canvas.x+canvas.width;gx+=30)
            rect(gx,canvas.y,1,canvas.height,0x304E5D75);
        for(int gy=canvas.y+30;gy<canvas.y+canvas.height;gy+=30)
            rect(canvas.x,gy,canvas.width,1,0x304E5D75);

        Rect status=geometry.status(ui,statusPreviewWidth(),statusPreviewHeight());
        drawHudMiniature(status,null,hudControls.selected()==HudEditorControls.Item.STATUS);

        for(Widget widget:Widget.values()) {
            Rect r=hudPreviewRect(geometry,widget);
            drawHudMiniature(r,widget,
                hudControls.selected()==HudEditorControls.itemFor(widget));
        }
        // Highlight only collisions involving the selected HUD; no automatic moves.
        Rect selectedRect=hudControls.selected()==HudEditorControls.Item.STATUS
            ?status:hudPreviewRect(geometry,hudControls.selected().widget);
        boolean hudOverlaps=false;
        if(HudOverlapPolicy.overlaps(selectedRect,status) &&
           hudControls.selected()!=HudEditorControls.Item.STATUS) {
            hudConflictOutline(status);hudOverlaps=true;
        }
        for(Widget peer:Widget.values()) {
            if(peer==hudControls.selected().widget)continue;
            Rect other=hudPreviewRect(geometry,peer);
            if(HudOverlapPolicy.overlaps(selectedRect,other)) {
                hudConflictOutline(other);hudOverlaps=true;
            }
        }
        if(hudOverlaps)hudConflictOutline(selectedRect);
        if(hudDragging) {
            if(snapGuideX!=HudSnapPolicy.NO_GUIDE)
                rect(Math.min(canvas.x+canvas.width-1,Math.max(canvas.x,snapGuideX)),
                    canvas.y,1,canvas.height,0x9966DDD0);
            if(snapGuideY!=HudSnapPolicy.NO_GUIDE)
                rect(canvas.x,Math.min(canvas.y+canvas.height-1,
                    Math.max(canvas.y,snapGuideY)),canvas.width,1,0x9966DDD0);
        }
        label(trim(hudOverlaps
            ?"HUD overlap detected | Drag to separate | R: Reset"
            :"1 Default 2 PvP 3 Stream | [ ] Resize | Alt: Free | 0: 1x | R: Position",
            w-side-20),
            previewX,previewY+previewH+8,0xFFBEC6D9);
    }
    /** Compact outline used only for editor feedback; never affects live HUD paint. */
    private void hudConflictOutline(Rect r) {
        final int warning=0xFFED9B8A;
        rect(r.x,r.y,r.width,1,warning);
        rect(r.x,r.y+r.height-1,r.width,1,warning);
        rect(r.x,r.y,1,r.height,warning);
        rect(r.x+r.width-1,r.y,1,r.height,warning);
    }
    /** A capture is tied to a stable setting, never the currently scrolled row. */
    private void adjustSliderDrag(int mouseX) {
        if(!sliderDrag.active())return;
        try {
            // Mutate only through UiActions; observe the actual applied
            // value so returning to the mouse-down value clears dirty state.
            Setting.Number number=(Setting.Number)host.modules()
                .get(sliderDrag.moduleId()).settings().get(sliderDrag.settingId());
            host.uiActions().previewFraction(sliderDrag.moduleId(),
                sliderDrag.settingId(),sliderDrag.fraction(mouseX));
            sliderDrag.observeValue(number.get());
        } catch(RuntimeException error) {
            finishSliderDrag();
            message="Setting rejected";
        }
    }
    /** Save once per completed/cancelled drag, never on each mouse-move. */
    private void finishSliderDrag() {
        if(sliderDrag.finish())host.uiActions().commitSlider();
    }
    /** Commit once on release, resize, tab switch or GUI closure.
     * Returning to the original anchor has no persistence side effect. */
    private boolean finishHudDrag() {
        if(!hudDragging)return false;
        boolean listMoved=draggingWidget==Widget.MODULE_LIST;
        boolean changed=hudCapture.finish(ui);
        hudDragging=false;draggingWidget=null;
        snapGuideX=HudSnapPolicy.NO_GUIDE;snapGuideY=HudSnapPolicy.NO_GUIDE;
        if(changed) {
            // Position setting changes once at commit, never for intermediate motion.
            if(listMoved)host.uiActions().choiceEdge("hud.modulelist","position",true);
            host.persistUi();
        }
        return changed;
    }
    private void adjustHudDrag(int mx,int my) {
        if(!hudDragging || !hudCapture.sameViewport(width,height))return;
        if(previewW<=0 || previewH<=0 ||
           !GuiChrome.interactive(GuiLayout.calculate(width,height,ui.layout())))return;
        // A pointer round-trip must restore the original *serialized*
        // normalized anchor, not quantize it through preview pixels.
        if(hudCapture.restoreAtPointerOrigin(ui,mx,my)) {
            snapGuideX=HudSnapPolicy.NO_GUIDE;snapGuideY=HudSnapPolicy.NO_GUIDE;
            return;
        }
        HudPreviewGeometry geometry=previewGeometry();
        if(draggingWidget==null)
            geometry.moveStatus(ui,statusPreviewWidth(),statusPreviewHeight(),
                mx,my,dragX,dragY);
        else
            geometry.moveWidget(ui.widgets(),draggingWidget,mx,my,dragX,dragY,
                hudWidth(draggingWidget),hudHeight(draggingWidget));
        snapGuideX=HudSnapPolicy.NO_GUIDE;
        snapGuideY=HudSnapPolicy.NO_GUIDE;
        if(Keyboard.isKeyDown(Keyboard.KEY_LMENU) ||
           Keyboard.isKeyDown(Keyboard.KEY_RMENU))return;
        Rect active=draggingWidget==null
            ? geometry.status(ui,statusPreviewWidth(),statusPreviewHeight())
            : hudPreviewRect(geometry,draggingWidget);
        Rect[] neighbors=new Rect[Widget.values().length+1];
        int count=0;
        if(draggingWidget!=null)
            neighbors[count++]=geometry.status(ui,statusPreviewWidth(),statusPreviewHeight());
        for(Widget widget:Widget.values())
            if(widget!=draggingWidget)
                neighbors[count++]=hudPreviewRect(geometry,widget);
        HudSnapPolicy.Result snapped=HudSnapPolicy.snap(geometry.canvas,active,
            java.util.Arrays.copyOf(neighbors,count),HudSnapPolicy.DEFAULT_TOLERANCE);
        snapGuideX=snapped.guideX;
        snapGuideY=snapped.guideY;
        // Project the snapped preview position through the same screen-aspect
        // geometry as pointer dragging, never directly to stored fractions.
        if(snapped.x!=active.x || snapped.y!=active.y) {
            if(draggingWidget==null)
                geometry.moveStatus(ui,statusPreviewWidth(),statusPreviewHeight(),
                    snapped.x,snapped.y,0,0);
            else
                geometry.moveWidget(ui.widgets(),draggingWidget,
                    snapped.x,snapped.y,0,0,
                    hudWidth(draggingWidget),hudHeight(draggingWidget));
        }
    }
    @Override protected void mouseClicked(int mx, int my, int button) throws IOException {
        moduleListFrameCache=null;
        finishSliderDrag();
        finishHudDrag();
        // Pointer focus takes precedence over a pending keybind editor.
        // Clicking the bind row itself will explicitly arm capture again
        // below; all other clicks cancel it before they can focus search,
        // switch category/module or interact with settings.
        capture=null;
        pendingControlBind=0;
        super.mouseClicked(mx, my, button);
        rebuild();
        GuiLayout zone = GuiLayout.calculate(width,height,ui.layout());
        if (button != 0 || !GuiChrome.interactive(zone) ||
            !inside(mx,my,x,y,w,h)) return;
        GuiChrome.Header header=GuiChrome.headerAt(zone,mx,my);
        if (header==GuiChrome.Header.LAYOUT) {
            ui.layout(ui.layout() == UiState.Layout.WIDE ? UiState.Layout.COMPACT : UiState.Layout.WIDE);
            rebuild();
            host.persistUi(); return;
        }
        if (header==GuiChrome.Header.THEME) {
            ui.theme(GuiThemePalette.next(ui.theme()));
            host.persistUi(); return;
        }
        if (header==GuiChrome.Header.MODULES) { hudPage = false; return; }
        if (header==GuiChrome.Header.HUD) {
            hudPage = true; capture = null; pendingControlBind=0; keyboardSetting=-1; return;
        }
        if (hudPage) {
            if (previewW<1 || previewH<1) return;
            Preset preset=HudLayoutPresets.buttonAt(x,side,y,h,mx,my);
            if(preset!=null) { applyHudLayoutPreset(preset); return; }
            HudEditorControls.Item picked=HudEditorControls.tabAt(
                x+side+12,y+62,w-side-24,mx,my);
            if(picked!=null) {
                hudControls.select(picked);
                return;
            }
            HudPreviewGeometry geometry=previewGeometry();
            if (!geometry.canvas.contains(mx,my)) return;
            // Reverse paint order: the topmost widget receives the click.
            Widget[] all=Widget.values();
            for(int i=all.length-1;i>=0;i--) {
                Widget widget=all[i];
                Rect r=hudPreviewRect(geometry,widget);
                if(r.contains(mx,my)) {
                    hudControls.select(HudEditorControls.itemFor(widget));
                    if(widget==Widget.MODULE_LIST)
                        captureModuleListOrigin(geometry,r);
                    hudCapture.begin(ui,widget,width,height,mx,my);
                    hudDragging=true;draggingWidget=widget;
                    dragX=mx-r.x;dragY=my-r.y;
                    return;
                }
            }
            Rect status=geometry.status(ui,statusPreviewWidth(),statusPreviewHeight());
            if(status.contains(mx,my)) {
                hudControls.select(HudEditorControls.Item.STATUS);
                hudCapture.begin(ui,null,width,height,mx,my);
                hudDragging=true;draggingWidget=null;
                dragX=mx-status.x;dragY=my-status.y;
            }
            return;
        }
        if (search != null) {
            search.mouseClicked(mx,my,button);
            if(search.isFocused())keyboardSetting=-1;
        }
        int category=GuiChrome.categoryIndex(zone,mx,my);
        if (category>=0) {
            ui.category(category==0 ? null : GhostModule.Category.valueOf(
                new String[]{"ALL","COMBAT","MOVEMENT","WORLD","RENDER","UTILITY"}[category]));
            scroll=0;
            keyboardSetting=-1;
            entries(); // immediately invalidate settings/key capture from the old filter
            return;
        }
        List<ModuleRegistry.Entry> filtered = entries();
        GuiNebulaWorkspace split=nebulaWorkspace(zone);
        if(split!=null) {
            int index=split.moduleIndex(mx,my,scroll,filtered.size());
            if(index>=0) {
                ModuleRegistry.Entry entry=filtered.get(index);
                if(split.moduleToggleArea(mx)) {
                    try {host.uiActions().toggle(entry.module().id());message="";}
                    catch(RuntimeException ex){message="Unable to toggle module";}
                } else {
                    selected=entry.module().id();
                    settingScroll=0;keyboardSetting=-1;
                }
                return;
            }
            if(selected==null)return;
            ModuleRegistry.Entry entry=host.modules().get(selected);
            if(split.keybindArea(mx,my)) {
                capture=selected;pendingControlBind=0;
                keyboardSetting=-1;message="";return;
            }
            List<Map.Entry<String,Setting<?>>> settings=
                new ArrayList<Map.Entry<String,Setting<?>>>(entry.settings().entrySet());
            int idx=split.settingIndex(mx,my,settingScroll,settings.size());
            if(idx>=0) {
                keyboardSetting=idx;
                Map.Entry<String,Setting<?>> item=settings.get(idx);
                try {
                    Setting<?> setting=item.getValue();
                    if(setting instanceof Setting.Bool)
                        host.uiActions().bool(selected,item.getKey());
                    else if(setting instanceof Setting.Choice)
                        host.uiActions().cycle(selected,item.getKey());
                    else if(setting instanceof Setting.Number) {
                        int yy=split.settingY+(idx-settingScroll)*30;
                        if(inside(mx,my,split.sliderX(),yy,split.sliderWidth(),27)) {
                            sliderDrag.begin(selected,item.getKey(),split.sliderX(),
                                split.sliderWidth(),width,height,
                                ((Setting.Number)setting).get());
                            adjustSliderDrag(mx);
                        }
                    }
                    message="";
                } catch(RuntimeException ex){message="Setting rejected";}
            }
            return;
        }
        if (GuiHitZones.moduleArea(zone,mx,my)) {
            int idx = GuiHitZones.moduleIndex(zone,mx,my,scroll,filtered.size());
            if (idx >= 0) {
                ModuleRegistry.Entry e = filtered.get(idx);
                if (mx >= x+w-60) {
                    try { host.uiActions().toggle(e.module().id()); message = ""; }
                    catch (RuntimeException ex) { message = "Unable to toggle: " + e.module().id(); }
                } else {
                    selected = e.module().id();
                    settingScroll = 0;
                    keyboardSetting=-1;
                }
            }
            return;
        }
        if (selected == null) return;
        ModuleRegistry.Entry e = host.modules().get(selected);
        int settingY = listY + listH + 5 + 22;
        if (inside(mx,my,x+side+11,settingY,w-side-26,18)) {
            capture = selected; pendingControlBind=0;
            keyboardSetting=-1; message = ""; return;
        }
        List<Map.Entry<String, Setting<?>>> settings = new ArrayList<Map.Entry<String, Setting<?>>>(e.settings().entrySet());
        int index = GuiHitZones.settingIndex(zone,mx,my,settingScroll,settings.size());
        if (index >= 0) {
            keyboardSetting=index;
            Map.Entry<String, Setting<?>> it = settings.get(index);
            try {
                Setting<?> setting = it.getValue();
                if (setting instanceof Setting.Bool) host.uiActions().bool(selected, it.getKey());
                else if (setting instanceof Setting.Choice) host.uiActions().cycle(selected, it.getKey());
                else if (setting instanceof Setting.Number) {
                    int right = x+w-17;
                    int barWidth = Math.max(33,(w-side)/3);
                    if (inside(mx,my,right-barWidth,GuiHitZones.settingStartY(zone)+
                            (index-settingScroll)*22,barWidth,19)) {
                        sliderDrag.begin(selected,it.getKey(),right-barWidth,barWidth,width,height,
                            ((Setting.Number)setting).get());
                        adjustSliderDrag(mx);
                    }
                }
                message = "";
            } catch (RuntimeException ex) { message = "Setting rejected"; }
        }
    }
    @Override protected void mouseClickMove(int mx,int my,int button,long timeSinceLastClick) {
        moduleListFrameCache=null;
        if(sliderDrag.active()) {
            if(button==0 && !hudPage &&
               sliderDrag.sameViewport(width,height) &&
               GuiChrome.interactive(GuiLayout.calculate(width,height,ui.layout())))
                adjustSliderDrag(mx);
            else finishSliderDrag();
            return;
        }
        if(hudDragging) {
            if(button==0)adjustHudDrag(mx,my);
            else finishHudDrag();
        }
    }
    @Override protected void mouseReleased(int mx,int my,int state) {
        moduleListFrameCache=null;
        super.mouseReleased(mx,my,state);
        if(sliderDrag.active()) {
            if(state==0 && sliderDrag.sameViewport(width,height))adjustSliderDrag(mx);
            finishSliderDrag();
        }
        if(hudDragging) {
            if(state==0)adjustHudDrag(mx,my);
            finishHudDrag();
        }
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && !hudPage) {
            finishSliderDrag();
            rebuild();
            GuiLayout zone=GuiLayout.calculate(width,height,ui.layout());
            if (!GuiChrome.interactive(zone)) return;
            int direction = wheel > 0 ? -1 : 1;
            int mx = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
            int my = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
            GuiNebulaWorkspace split=nebulaWorkspace(zone);
            if((split!=null&&split.settingArea(mx,my)) ||
               (split==null&&GuiHitZones.settingsArea(zone,mx,my))) {
                entries();
                if (selected == null) return;
                ModuleRegistry.Entry e = host.modules().get(selected);
                int max = Math.max(0,e.settings().size()-settingsRows(zone));
                settingScroll = Math.max(0,Math.min(max, settingScroll+direction));
                // Wheel scrolling is manual: never reveal the formerly
                // focused row by undoing the user's scroll, and never
                // mutate a now-hidden setting from later arrow input.
                keyboardSetting=GuiSettingKeyboard.visibleFocus(
                    keyboardSetting,settingScroll,e.settings().size(),
                    settingsRows(zone));
            } else if((split!=null&&split.moduleArea(mx,my)) ||
                      (split==null&&GuiHitZones.moduleArea(zone,mx,my))) {
                int max = Math.max(0,entries().size() - visibleRows);
                scroll = Math.max(0,Math.min(max,scroll + direction));
            }
        }
    }
    /**
     * GuiScreen.keyTyped sees key-down only. Observe the matching Ctrl
     * release here so Ctrl alone remains bindable without consuming the
     * capture before a following F can transfer focus to search.
     */
    @Override public void handleKeyboardInput() throws IOException {
        if(!Keyboard.getEventKeyState() &&
           GuiKeybindCapture.commitDeferredControl(capture!=null,
               pendingControlBind,Keyboard.getEventKey())) {
            String id=capture;
            int control=pendingControlBind;
            capture=null;
            pendingControlBind=0;
            try {host.uiActions().bind(id,control);message="";}
            catch(RuntimeException ex){message="Key already assigned or invalid";}
        }
        super.handleKeyboardInput();
    }
    @Override protected void keyTyped(char character,int keyCode) throws IOException {
        if (!GuiChrome.interactive(GuiLayout.calculate(width,height,ui.layout()))) {
            if (keyCode==Keyboard.KEY_ESCAPE || keyCode==Keyboard.KEY_F7)
                mc.displayGuiScreen(null);
            return;
        }
        // Ctrl+F is an explicit transfer to search, even if a pending
        // keyboard/mouse bind capture would otherwise consume KEY_F.
        if(GuiKeybindCapture.transferToSearch(capture!=null,!hudPage,
                keyCode==Keyboard.KEY_F,
                Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) ||
                Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
            capture=null;
            pendingControlBind=0;
        }
        if (capture != null) {
            // Key repeat from the B shortcut or the bound key must never
            // silently become a new module binding.
            if(Keyboard.isRepeatEvent())return;
            if(GuiKeybindCapture.deferControl(capture!=null,
                keyCode==Keyboard.KEY_LCONTROL ||
                keyCode==Keyboard.KEY_RCONTROL)) {
                pendingControlBind=keyCode;
                return;
            }
            String id = capture; capture = null; pendingControlBind=0;
            if (keyCode == Keyboard.KEY_ESCAPE) return;
            if (keyCode == Keyboard.KEY_F7) {
                mc.displayGuiScreen(null);
                return;
            }
            if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) keyCode = 0;
            try { host.uiActions().bind(id,keyCode); message = ""; }
            catch (RuntimeException ex) { message = "Key already assigned or invalid"; }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_F7) {
            mc.displayGuiScreen(null); return;
        }
        if (hudPage) {
            // Quick layouts are F7-only shortcuts; modules never see these keys.
            if(keyCode==Keyboard.KEY_1 || keyCode==Keyboard.KEY_2 ||
               keyCode==Keyboard.KEY_3 ||
               keyCode==Keyboard.KEY_NUMPAD1 ||
               keyCode==Keyboard.KEY_NUMPAD2 ||
               keyCode==Keyboard.KEY_NUMPAD3) {
                if(Keyboard.isRepeatEvent())return;
                Preset preset=(keyCode==Keyboard.KEY_1 ||
                               keyCode==Keyboard.KEY_NUMPAD1)?Preset.DEFAULT:
                              (keyCode==Keyboard.KEY_2 ||
                               keyCode==Keyboard.KEY_NUMPAD2)?Preset.PVP:Preset.STREAM;
                applyHudLayoutPreset(preset);
                return;
            }
            // Scale the selected live widget, never the preview illustration only.
            if(keyCode==Keyboard.KEY_LBRACKET || keyCode==Keyboard.KEY_RBRACKET ||
               keyCode==Keyboard.KEY_0) {
                if(Keyboard.isRepeatEvent())return;
                finishHudDrag();
                String visualId=HudScaleEditor.moduleId(hudControls.selected());
                try {
                    if(keyCode==Keyboard.KEY_0) {
                        host.uiActions().reset(visualId,"scale");
                    } else {
                        int direction=keyCode==Keyboard.KEY_RBRACKET?1:-1;
                        boolean coarse=Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ||
                                       Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                        dev.ghostfusion.core.Setting.Number current=
                            (dev.ghostfusion.core.Setting.Number)host.modules()
                                .get(visualId).settings().get("scale");
                        double next=HudScaleEditor.adjust(current.get(),direction,coarse);
                        if(Double.compare(next,current.get())!=0)
                            host.uiActions().fraction(visualId,"scale",
                                HudScaleEditor.fraction(next));
                    }
                    moduleListFrameCache=null;
                } catch(RuntimeException error) { message="HUD scale rejected"; }
                return;
            }
            if (keyCode==Keyboard.KEY_R) {
                boolean changed=hudControls.resetIfChanged(ui);
                if(hudControls.selected()==HudEditorControls.Item.MODULE_LIST)
                    host.uiActions().reset("hud.modulelist","position");
                if(changed)host.persistUi();
                return;
            }
            int dx=0,dy=0;
            if(keyCode==Keyboard.KEY_LEFT)dx=-1;
            else if(keyCode==Keyboard.KEY_RIGHT)dx=1;
            else if(keyCode==Keyboard.KEY_UP)dy=-1;
            else if(keyCode==Keyboard.KEY_DOWN)dy=1;
            if(dx!=0 || dy!=0) {
                int step=(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ||
                          Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))?10:1;
                HudEditorControls.Item item=hudControls.selected();
                int cw=item==HudEditorControls.Item.STATUS ?
                    statusPreviewWidth():hudWidth(item.widget);
                int ch=item==HudEditorControls.Item.STATUS ?
                    statusPreviewHeight():hudHeight(item.widget);
                if(item==HudEditorControls.Item.MODULE_LIST &&
                   !"custom".equals(host.moduleListPosition())) {
                    HudPreviewGeometry geometry=previewGeometry();
                    captureModuleListOrigin(geometry,
                        hudPreviewRect(geometry,Widget.MODULE_LIST));
                }
                if(hudControls.nudgeIfChanged(ui,dx*step,dy*step,
                                               width,height,cw,ch)) {
                    if(item==HudEditorControls.Item.MODULE_LIST)
                        host.uiActions().choiceEdge("hud.modulelist","position",true);
                    host.persistUi();
                }
                return;
            }
        }
        if (!hudPage && keyCode==Keyboard.KEY_F &&
            (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) ||
             Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
            // Ctrl+F explicitly transfers keyboard ownership from modules
            // and settings to the search field without editing profiles.
            finishSliderDrag();
            keyboardSetting=-1;
            if(search!=null) {
                search.setFocused(true);
                search.setCursorPositionEnd();
                search.setSelectionPos(0);
            }
            return;
        }
        // Search gets first refusal while focused. Enter releases focus;
        // arrows and editing keys must never toggle or move modules.
        GuiSearchFocus.Route searchRoute=GuiSearchFocus.route(
            !hudPage && search!=null && search.isFocused(),
            keyCode==Keyboard.KEY_RETURN || keyCode==Keyboard.KEY_NUMPADENTER,
            keyCode==Keyboard.KEY_TAB);
        if(searchRoute==GuiSearchFocus.Route.RELEASE) {
            search.setFocused(false);
            return;
        }
        if(searchRoute==GuiSearchFocus.Route.EDIT) {
            String before=search.getText();
            search.textboxKeyTyped(character,keyCode);
            if(!before.equals(search.getText())) {
                ui.query(search.getText());
                scroll=0;
                settingScroll=0;
                keyboardSetting=-1;
                entries();
            }
            return;
        }
        if (keyCode==Keyboard.KEY_B &&
            GuiKeybindCapture.arm(!hudPage,search!=null && search.isFocused(),
                keyboardSetting,selected,Keyboard.isRepeatEvent())) {
            finishSliderDrag();
            entries(); // reconcile the current search/category filter first
            if(selected!=null) {
                capture=selected;pendingControlBind=0;message="";
            }
            return;
        }
        if (!hudPage && keyCode==Keyboard.KEY_TAB) {
            finishSliderDrag();
            if(search!=null)search.setFocused(false);
            entries(); // remove stale module focus before setting traversal
            if(selected==null) {keyboardSetting=-1; return;}
            int count=host.modules().get(selected).settings().size();
            int rows=settingsRows(
                GuiLayout.calculate(width,height,ui.layout()));
            keyboardSetting=rows>0?GuiSettingKeyboard.next(
                keyboardSetting,count,(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ||
                Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))?-1:1):-1;
            settingScroll=GuiSettingKeyboard.reveal(
                keyboardSetting,settingScroll,count,rows);
            return;
        }
        if (!hudPage && keyboardSetting>=0 &&
            (search==null || !search.isFocused()) &&
            (keyCode==Keyboard.KEY_RETURN || keyCode==Keyboard.KEY_NUMPADENTER ||
             keyCode==Keyboard.KEY_SPACE || keyCode==Keyboard.KEY_LEFT ||
             keyCode==Keyboard.KEY_RIGHT || keyCode==Keyboard.KEY_R ||
             keyCode==Keyboard.KEY_HOME || keyCode==Keyboard.KEY_END)) {
            entries();
            if(selected!=null && keyboardSetting>=0) {
                keyboardSetting=GuiSettingKeyboard.visibleFocus(
                    keyboardSetting,settingScroll,
                    host.modules().get(selected).settings().size(),
                    settingsRows(GuiLayout.calculate(width,height,ui.layout())));
            }
            if(selected!=null && keyboardSetting>=0) {
                List<Map.Entry<String, Setting<?>>> settings=
                    new ArrayList<Map.Entry<String, Setting<?>>>(host.modules().get(selected)
                        .settings().entrySet());
                if(keyboardSetting<settings.size()) {
                    Map.Entry<String, Setting<?>> item=settings.get(keyboardSetting);
                    Setting<?> s=item.getValue();
                    try {
                        if(keyCode==Keyboard.KEY_R) {
                            // A focused setting only: reset its own original
                            // typed default, not the entire module/profile.
                            if(!Keyboard.isRepeatEvent())
                                host.uiActions().reset(selected,item.getKey());
                        } else if(keyCode==Keyboard.KEY_SPACE || keyCode==Keyboard.KEY_RETURN ||
                           keyCode==Keyboard.KEY_NUMPADENTER) {
                            if(s instanceof Setting.Bool)
                                host.uiActions().bool(selected,item.getKey());
                            else if(s instanceof Setting.Choice)
                                host.uiActions().cycle(selected,item.getKey());
                        } else if(s instanceof Setting.Choice) {
                            if(keyCode==Keyboard.KEY_HOME || keyCode==Keyboard.KEY_END)
                                host.uiActions().choiceEdge(selected,item.getKey(),
                                    keyCode==Keyboard.KEY_END);
                            else if(keyCode==Keyboard.KEY_LEFT || keyCode==Keyboard.KEY_RIGHT)
                                host.uiActions().cycle(selected,item.getKey(),
                                    keyCode==Keyboard.KEY_RIGHT?1:-1);
                        } else if(s instanceof Setting.Number) {
                            Setting.Number n=(Setting.Number)s;
                            double oldFraction=NumericSliderScale.fraction(
                                n.get(),n.min(),n.max());
                            double next=(keyCode==Keyboard.KEY_HOME ||
                                         keyCode==Keyboard.KEY_END)
                                ?GuiSettingKeyboard.endpointFraction(n.min(),n.max(),
                                    keyCode==Keyboard.KEY_END)
                                :GuiSettingKeyboard.nudgeFraction(
                                    n.get(),n.min(),n.max(),
                                    keyCode==Keyboard.KEY_RIGHT?1:-1,
                                    Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ||
                                    Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
                            if(n.min()!=n.max() && Double.compare(oldFraction,next)!=0)
                                host.uiActions().fraction(selected,item.getKey(),next);
                        }
                        message="";
                    } catch(RuntimeException ex) {message="Setting rejected";}
                }
            }
            return;
        }
        if (!hudPage && (keyCode==Keyboard.KEY_UP || keyCode==Keyboard.KEY_DOWN)) {
            finishSliderDrag();
            keyboardSetting=-1;
            List<ModuleRegistry.Entry> filtered=entries();
            String next=GuiSelection.step(selected,filtered,
                keyCode==Keyboard.KEY_DOWN?1:-1);
            if(selected==null ? next!=null : !selected.equals(next)) {
                selected=next;
                settingScroll=0;
                capture=null;
                pendingControlBind=0;
            }
            scroll=GuiSelection.reveal(selected,filtered,scroll,Math.max(1,visibleRows));
            return;
        }
        if (!hudPage && (keyCode==Keyboard.KEY_RETURN ||
                         keyCode==Keyboard.KEY_NUMPADENTER)) {
            List<ModuleRegistry.Entry> filtered=entries();
            if(selected!=null) {
                scroll=GuiSelection.reveal(selected,filtered,scroll,Math.max(1,visibleRows));
                try { host.uiActions().toggle(selected); message=""; }
                catch(RuntimeException ex) { message="Unable to toggle: "+selected; }
            }
            return;
        }
        super.keyTyped(character,keyCode);
    }
    @Override public void onGuiClosed() {
        finishSliderDrag();
        boolean endedHudDrag=hudDragging;
        finishHudDrag();
        host.diagnostics().record(Counter.GUI_CLOSE);
        Keyboard.enableRepeatEvents(false);
        hudDragging = false;
        draggingWidget = null;
        capture = null;
        pendingControlBind=0;
        keyboardSetting=-1;
        // A capture that changed was already committed by finishHudDrag;
        // otherwise retain the existing close-time preferences checkpoint.
        if(!endedHudDrag)host.persistUi();
        host.inputFocus(true);
        super.onGuiClosed();
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
