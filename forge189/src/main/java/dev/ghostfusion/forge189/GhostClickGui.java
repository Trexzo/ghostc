package dev.ghostfusion.forge189;

import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ReservedKeys;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.Setting;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import dev.ghostfusion.ui.UiState;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.GuiHitZones;
import dev.ghostfusion.ui.GuiChrome;
import dev.ghostfusion.ui.GuiSelection;
import dev.ghostfusion.ui.GuiSettingKeyboard;
import dev.ghostfusion.ui.HudPositions.Widget;
import dev.ghostfusion.ui.HudPreviewGeometry;
import dev.ghostfusion.ui.HudFootprints;
import dev.ghostfusion.ui.HudTextFit;
import dev.ghostfusion.ui.GuiSliderDrag;
import dev.ghostfusion.ui.NumericSliderScale;
import dev.ghostfusion.ui.StatusHudLayout;
import dev.ghostfusion.ui.HudEditorControls;
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
    private String message = "";
    private boolean hudPage;
    private boolean hudDragging;
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
        previewX=g.previewX; previewY=g.previewY;
        previewW=g.previewWidth; previewH=g.previewHeight;
        if (!GuiChrome.interactive(g)) {
            hudDragging=false;
            draggingWidget=null;
            capture=null;
        }
        syncSearch(g);
    }
    private void syncSearch(GuiLayout g) {
        if (search!=null && searchPositionX==g.searchX &&
            searchPositionY==g.searchY && searchPositionWidth==g.searchWidth) return;
        String previous=search==null?ui.query():search.getText();
        boolean focused=search!=null && search.isFocused();
        GuiTextField resized=new GuiTextField(42,fontRendererObj,
            g.searchX,g.searchY,g.searchWidth,17);
        resized.setMaxStringLength(100);
        resized.setText(previous);
        resized.setFocused(focused);
        search=resized;
        searchPositionX=g.searchX; searchPositionY=g.searchY;
        searchPositionWidth=g.searchWidth;
    }
    private int bg() { return ui.theme() == UiState.Theme.INDIGO ? 0xF3161A2C : 0xF31A1C20; }
    private int panel() { return ui.theme() == UiState.Theme.INDIGO ? 0xFF22283E : 0xFF303339; }
    private int accent() { return ui.theme() == UiState.Theme.INDIGO ? 0xFF9A8CFF : 0xFF69D9C6; }
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
        rect(0, 0, width, height, 0x99060B13);
        rect(x, y, w, h, bg());
        if (!GuiChrome.interactive(GuiLayout.calculate(width,height,ui.layout()))) {
            label(trim("GhostC: enlarge game window or reduce GUI scale",w-16),
                x+8,y+Math.min(18,Math.max(0,h-1)),accent());
            return;
        }
        rect(x, y, side, h, panel());
        rect(x, y, w, 30, 0xBB080C1A);
        label("GHOSTC", x + 10, y + 10, accent());
        int menuX = x + side + 8;
        label(hudPage ? "MODULES" : "[MODULES]", menuX, y + 11, hudPage ? 0xFFB5B6C1 : 0xFFFFFFFF);
        label(hudPage ? "[HUD]" : "HUD", menuX + 76, y + 11, hudPage ? 0xFFFFFFFF : 0xFFB5B6C1);
        rect(x + w - 99, y + 6, 40, 17, panel());
        rect(x + w - 55, y + 6, 50, 17, panel());
        label(ui.layout() == UiState.Layout.WIDE ? "WIDE" : "SMALL", x + w - 96, y + 10, 0xFFF2F3FF);
        label(ui.theme() == UiState.Theme.INDIGO ? "INDIGO" : "MINT", x + w - 52, y + 10, accent());
        if (hudPage) drawHudEditor(mx, my);
        else drawModules(mx, my);
        if (!message.isEmpty()) label(message, x + side + 11, y + h - 13, 0xFFFFB3A6);
        else if (!host.uiWarning().isEmpty()) label(host.uiWarning(), x + side + 11, y + h - 13, 0xFFFFB3A6);
    }
    private void drawSidebar() {
        int yy = y + 47;
        label("FILTER", x + 10, yy, 0xFF979DAF);
        yy += 18;
        String[] names = {"ALL", "COMBAT", "MOVEMENT", "WORLD", "RENDER", "UTILITY"};
        for (int i = 0; i < names.length; i++) {
            GhostModule.Category c = i == 0 ? null : GhostModule.Category.valueOf(names[i]);
            boolean active = ui.category() == c;
            if (active) rect(x + 4, yy - 3, side - 8, 19, 0x556B83EA);
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
            scroll=GuiSelection.reveal(selected,filtered,scroll,Math.max(1,visibleRows));
        }
        return filtered;
    }
    private void drawModules(int mx, int my) {
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
            rect(x + side + 9, ry, w - side - 20, 25, focused ? 0x664B597D : 0x442D344A);
            label(trim(e.module().displayName(), Math.max(60, w - side - 97)),
                    x + side + 15, ry + 8, focused ? 0xFFFFFFFF : 0xFFE4E7F0);
            boolean enabled = e.state() == ModuleRegistry.State.ENABLED;
            int right = x + w - 54;
            rect(right, ry + 5, 39, 15, enabled ? accent() : 0xFF586073);
            label(enabled ? "ON" : "OFF", right + 9, ry + 8, enabled ? 0xFF101320 : 0xFFFFFFFF);
        }
        int sy = listY + listH + 5;
        rect(x + side + 9, sy - 4, w - side - 20, 1, 0x557E879D);
        if (selected == null) {
            keyboardSetting=-1;
            label("No matching modules", x + side + 13, sy + 5, 0xFFBAC1D3);
            return;
        }
        ModuleRegistry.Entry e;
        try { e = host.modules().get(selected); }
        catch (IllegalArgumentException ex) { return; }
        label("SETTINGS  " + e.module().displayName(), x + side + 13, sy + 4, accent());
        int bindY = sy + 22;
        rect(x + side + 11, bindY, w - side - 26, 18, 0x66394359);
        String keyText = capture != null && capture.equals(selected) ? "Press key (ESC cancel / BACKSPACE clear)" :
            "KEYBIND: " + (e.keybind() == 0 ? "NONE" : Keyboard.getKeyName(e.keybind()));
        label(trim(keyText, w - side - 39), x + side + 16, bindY + 5, 0xFFEAECF5);
        int sY = bindY + 24;
        List<Map.Entry<String, Setting<?>>> settings = new ArrayList<Map.Entry<String, Setting<?>>>(e.settings().entrySet());
        int available = Math.max(0, (y + h - 18 - sY) / 22);
        if(keyboardSetting>=settings.size())keyboardSetting=-1;
        settingScroll = Math.max(0, Math.min(settingScroll, Math.max(0, settings.size() - available)));
        for (int i = settingScroll; i < settings.size() && i < settingScroll + available; i++) {
            Map.Entry<String, Setting<?>> kv = settings.get(i);
            Setting<?> setting = kv.getValue();
            rect(x + side + 11, sY, w - side - 26, 19,
                i==keyboardSetting ? 0x886A588D : 0x443B465C);
            label(trim(setting.id(), Math.max(30, (w - side) / 2 - 14)), x + side + 15, sY + 5, 0xFFE0E4EF);
            int right = x + w - 17;
            if (setting instanceof Setting.Number) {
                Setting.Number number = (Setting.Number) setting;
                double fraction = NumericSliderScale.fraction(
                    number.get(),number.min(),number.max());
                int barWidth = Math.max(33, (w - side) / 3);
                rect(right - barWidth, sY + 7, barWidth, 6, 0xFF525A6C);
                rect(right - barWidth, sY + 7, (int) (barWidth * fraction), 6, accent());
                label(String.format(java.util.Locale.ROOT, "%.2f", number.get()), right - barWidth - 37, sY + 5, 0xFFC6CBD7);
            } else {
                label(trim(setting.serialize(), Math.max(30, (w - side) / 3)),
                    x + w - 17 - Math.min((w - side) / 3, 90), sY + 5, accent());
            }
            sY += 22;
        }
        if (keyboardSetting>=0 && available>0) label(
            "TAB  SPACE/ENTER  LEFT/RIGHT",x+side+13,bindY+6,0xFFE7E8F5);
        else if (settings.size() > available) label(
            (settingScroll + 1) + "-" + Math.min(settings.size(), settingScroll + available) +
            "/" + settings.size() + "  scroll", x + side + 13, bindY + 6, 0xFFE7E8F5);
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
    private int hudWidth(Widget widget) {
        return HudFootprints.width(widget);
    }
    private int hudHeight(Widget widget) {
        return HudFootprints.height(widget,host.keysJump(),host.keysSneak(),host.targetHealth());
    }
    private StatusHudLayout statusFootprint() {
        boolean status=host.featureEnabled("hud.status");
        boolean metrics=host.featureEnabled("hud.metrics");
        boolean fps=metrics && host.showFps();
        boolean coordinates=metrics && host.showCoordinates() && mc.thePlayer!=null;
        // The Status anchor remains selectable even when both modules are off.
        if(!status && !fps && !coordinates) status=true;
        String title=host.detailedStatus()
            ? "GhostC "+GhostCMod.CLIENT_VERSION+" | F7 ClickGUI" : "GhostC";
        float scale=host.statusScale();
        int statusW=Math.max(1,Math.round((fontRendererObj.getStringWidth(title)+4)*scale));
        int statusH=Math.max(1,Math.round((fontRendererObj.FONT_HEIGHT+5)*scale));
        String fpsText=fps?"FPS "+Minecraft.getDebugFPS():"";
        String coordsText=coordinates
            ? String.format(java.util.Locale.ROOT,"XYZ %.1f %.1f %.1f",
                mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ) : "";
        return StatusHudLayout.calculate(ui,width,height,status,fps,coordinates,
            statusW,statusH,fontRendererObj.getStringWidth(fpsText),
            fontRendererObj.getStringWidth(coordsText),fontRendererObj.FONT_HEIGHT);
    }
    private int statusPreviewWidth() { return statusFootprint().measuredWidth; }
    private int statusPreviewHeight() { return statusFootprint().measuredHeight; }
    private void drawHudEditor(int mx, int my) {
        if (previewW<1 || previewH<1) return;
        label("HUD POSITION EDITOR",x+side+12,y+49,accent());
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
        rect(status.x,status.y,status.width,status.height,
            hudControls.selected()==HudEditorControls.Item.STATUS ? 0xFF8376CD : 0xFF444C78);
        if(status.width>=30 && status.height>=12)
            label("Status",status.x+2,status.y+2,0xFFFFFFFF);

        for(Widget widget:Widget.values()) {
            Rect r=geometry.widget(ui.widgets(),widget,hudWidth(widget),hudHeight(widget));
            rect(r.x,r.y,r.width,r.height,
                hudControls.selected()==HudEditorControls.itemFor(widget) ?
                    0xFF8376CD : 0xFF384B66);
            String tag=widget==Widget.KEYSTROKES?"K":widget==Widget.CPS?"C":"T";
            if(r.width>=8 && r.height>=10)
                label(tag,r.x+1,r.y+1,0xFFFFFFFF);
        }
        label(trim("Arrows: 1px | Shift+Arrow: 10px | R: Reset",w-side-20),
            previewX,previewY+previewH+8,0xFFBEC6D9);
    }
    /** A capture is tied to a stable setting, never the currently scrolled row. */
    private void adjustSliderDrag(int mouseX) {
        if(!sliderDrag.active())return;
        try {
            if(host.uiActions().previewFraction(sliderDrag.moduleId(),
                   sliderDrag.settingId(),sliderDrag.fraction(mouseX)))
                sliderDrag.changed();
        } catch(RuntimeException error) {
            finishSliderDrag();
            message="Setting rejected";
        }
    }
    /** Save once per completed/cancelled drag, never on each mouse-move. */
    private void finishSliderDrag() {
        if(sliderDrag.finish())host.uiActions().commitSlider();
    }
    @Override protected void mouseClicked(int mx, int my, int button) throws IOException {
        finishSliderDrag();
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
            ui.theme(ui.theme() == UiState.Theme.INDIGO ? UiState.Theme.GRAPHITE : UiState.Theme.INDIGO);
            host.persistUi(); return;
        }
        if (header==GuiChrome.Header.MODULES) { hudPage = false; return; }
        if (header==GuiChrome.Header.HUD) {
            hudPage = true; capture = null; keyboardSetting=-1; return;
        }
        if (hudPage) {
            if (previewW<1 || previewH<1) return;
            HudEditorControls.Item picked=HudEditorControls.tabAt(
                x+side+12,y+62,w-side-24,mx,my);
            if(picked!=null) {
                hudControls.select(picked);
                hudDragging=false;
                draggingWidget=null;
                return;
            }
            HudPreviewGeometry geometry=previewGeometry();
            if (!geometry.canvas.contains(mx,my)) return;
            // Reverse paint order: the topmost widget receives the click.
            Widget[] all=Widget.values();
            for(int i=all.length-1;i>=0;i--) {
                Widget widget=all[i];
                Rect r=geometry.widget(ui.widgets(),widget,hudWidth(widget),hudHeight(widget));
                if(r.contains(mx,my)) {
                    hudControls.select(HudEditorControls.itemFor(widget));
                    hudDragging=true;draggingWidget=widget;
                    dragX=mx-r.x;dragY=my-r.y;
                    return;
                }
            }
            Rect status=geometry.status(ui,statusPreviewWidth(),statusPreviewHeight());
            if(status.contains(mx,my)) {
                hudControls.select(HudEditorControls.Item.STATUS);
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
            capture = selected; keyboardSetting=-1; message = ""; return;
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
                        sliderDrag.begin(selected,it.getKey(),right-barWidth,barWidth,width,height);
                        adjustSliderDrag(mx);
                    }
                }
                message = "";
            } catch (RuntimeException ex) { message = "Setting rejected"; }
        }
    }
    @Override protected void mouseClickMove(int mx,int my,int button,long timeSinceLastClick) {
        if(sliderDrag.active()) {
            if(button==0 && !hudPage &&
               sliderDrag.sameViewport(width,height) &&
               GuiChrome.interactive(GuiLayout.calculate(width,height,ui.layout())))
                adjustSliderDrag(mx);
            else finishSliderDrag();
            return;
        }
        if (hudDragging && button==0 && previewW>0 && previewH>0 &&
            GuiChrome.interactive(GuiLayout.calculate(width,height,ui.layout()))) {
            HudPreviewGeometry geometry=previewGeometry();
            if(draggingWidget==null)
                geometry.moveStatus(ui,statusPreviewWidth(),statusPreviewHeight(),
                    mx,my,dragX,dragY);
            else
                geometry.moveWidget(ui.widgets(),draggingWidget,mx,my,dragX,dragY,
                    hudWidth(draggingWidget),hudHeight(draggingWidget));
        }
    }
    @Override protected void mouseReleased(int mx,int my,int state) {
        super.mouseReleased(mx,my,state);
        if(sliderDrag.active()) {
            if(state==0 && sliderDrag.sameViewport(width,height))adjustSliderDrag(mx);
            finishSliderDrag();
        }
        if (hudDragging) {
            hudDragging=false;
            draggingWidget=null;
            host.persistUi();
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
            if (GuiHitZones.settingsArea(zone,mx,my)) {
                entries();
                if (selected == null) return;
                ModuleRegistry.Entry e = host.modules().get(selected);
                int max = Math.max(0,e.settings().size()-GuiHitZones.visibleSettings(zone));
                settingScroll = Math.max(0,Math.min(max, settingScroll+direction));
            } else if (GuiHitZones.moduleArea(zone,mx,my)) {
                int max = Math.max(0,entries().size() - zone.visibleRows);
                scroll = Math.max(0,Math.min(max,scroll + direction));
            }
        }
    }
    @Override protected void keyTyped(char character,int keyCode) throws IOException {
        if (!GuiChrome.interactive(GuiLayout.calculate(width,height,ui.layout()))) {
            if (keyCode==Keyboard.KEY_ESCAPE || keyCode==Keyboard.KEY_F7)
                mc.displayGuiScreen(null);
            return;
        }
        if (capture != null) {
            String id = capture; capture = null;
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
            if (keyCode==Keyboard.KEY_R) {
                hudControls.reset(ui);
                host.persistUi();
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
                hudControls.nudge(ui,dx*step,dy*step,width,height,cw,ch);
                host.persistUi();
                return;
            }
        }
        if (!hudPage && keyCode==Keyboard.KEY_TAB) {
            finishSliderDrag();
            if(search!=null)search.setFocused(false);
            entries(); // remove stale module focus before setting traversal
            if(selected==null) {keyboardSetting=-1; return;}
            int count=host.modules().get(selected).settings().size();
            int rows=GuiHitZones.visibleSettings(
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
             keyCode==Keyboard.KEY_RIGHT)) {
            entries();
            if(selected!=null && keyboardSetting>=0) {
                List<Map.Entry<String, Setting<?>>> settings=
                    new ArrayList<Map.Entry<String, Setting<?>>>(host.modules().get(selected)
                        .settings().entrySet());
                if(keyboardSetting<settings.size()) {
                    Map.Entry<String, Setting<?>> item=settings.get(keyboardSetting);
                    Setting<?> s=item.getValue();
                    try {
                        if(keyCode==Keyboard.KEY_SPACE || keyCode==Keyboard.KEY_RETURN ||
                           keyCode==Keyboard.KEY_NUMPADENTER) {
                            if(s instanceof Setting.Bool)
                                host.uiActions().bool(selected,item.getKey());
                            else if(s instanceof Setting.Choice)
                                host.uiActions().cycle(selected,item.getKey());
                        } else if(s instanceof Setting.Number) {
                            Setting.Number n=(Setting.Number)s;
                            double oldFraction=NumericSliderScale.fraction(
                                n.get(),n.min(),n.max());
                            double next=GuiSettingKeyboard.nudgeFraction(
                                n.get(),n.min(),n.max(),
                                keyCode==Keyboard.KEY_RIGHT?1:-1);
                            if(Double.compare(oldFraction,next)!=0)
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
        if (!hudPage && search != null && search.textboxKeyTyped(character,keyCode)) {
            ui.query(search.getText());
            scroll=0;
            keyboardSetting=-1;
            entries(); // reject stale selected settings as the search changes
            return;
        }
        super.keyTyped(character,keyCode);
    }
    @Override public void onGuiClosed() {
        finishSliderDrag();
        host.diagnostics().record(Counter.GUI_CLOSE);
        Keyboard.enableRepeatEvents(false);
        hudDragging = false;
        draggingWidget = null;
        capture = null;
        keyboardSetting=-1;
        host.persistUi();
        host.inputFocus(true);
        super.onGuiClosed();
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
