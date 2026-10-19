package dev.ghostfusion.forge189;

import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.Setting;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import dev.ghostfusion.ui.UiState;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.GuiHitZones;
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
    private int dragX, dragY, scroll, settingScroll;
    private int x, y, w, h, side;
    private int listY, listH, visibleRows;
    private int previewX, previewY, previewW, previewH;
    private static final int HUD_WIDTH = 124;
    private static final int HUD_HEIGHT = 22;

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
        x=g.x; y=g.y; w=g.width; h=g.height; side=g.sidebar;
        listY=g.listY; listH=g.listHeight; visibleRows=g.visibleRows;
        previewX=g.previewX; previewY=g.previewY;
        previewW=g.previewWidth; previewH=g.previewHeight;
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
        label("F7: OPEN", x + 10, y + h - 35, 0xFF929BAC);
        label("ESC: CLOSE", x + 10, y + h - 23, 0xFF929BAC);
    }
    private List<ModuleRegistry.Entry> entries() {
        return ui.filtered(host.modules());
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
        settingScroll = Math.max(0, Math.min(settingScroll, Math.max(0, settings.size() - available)));
        for (int i = settingScroll; i < settings.size() && i < settingScroll + available; i++) {
            Map.Entry<String, Setting<?>> kv = settings.get(i);
            Setting<?> setting = kv.getValue();
            rect(x + side + 11, sY, w - side - 26, 19, 0x443B465C);
            label(trim(setting.id(), Math.max(30, (w - side) / 2 - 14)), x + side + 15, sY + 5, 0xFFE0E4EF);
            int right = x + w - 17;
            if (setting instanceof Setting.Number) {
                Setting.Number number = (Setting.Number) setting;
                double fraction = (number.get() - number.min()) / (number.max() - number.min());
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
        if (settings.size() > available) label(
            (settingScroll + 1) + "-" + Math.min(settings.size(), settingScroll + available) +
            "/" + settings.size() + "  scroll", x + side + 13, bindY + 6, 0xFFE7E8F5);
    }
    private String trim(String text, int maxPixels) {
        if (maxPixels < 12) return "";
        if (fontRendererObj.getStringWidth(text) <= maxPixels) return text;
        String out = text;
        while (out.length() > 0 && fontRendererObj.getStringWidth(out + "..") > maxPixels)
            out = out.substring(0, out.length() - 1);
        return out + "..";
    }
    private void drawHudEditor(int mx, int my) {
        if (previewW<1 || previewH<1) return;
        label("HUD POSITION EDITOR", x + side + 12, y + 49, accent());
        label("Drag the status preview. Coordinates survive restart.", x + side + 12,
            y + 62, 0xFFBAC1D3);
        rect(previewX, previewY, previewW, previewH, 0xFF0D1220);
        for (int gridX = previewX + 30; gridX < previewX + previewW; gridX += 30)
            rect(gridX, previewY, 1, previewH, 0x172D344A);
        for (int gridY = previewY + 30; gridY < previewY + previewH; gridY += 30)
            rect(previewX, gridY, previewW, 1, 0x172D344A);
        int hx = previewX + ui.pixelX(previewW, HUD_WIDTH);
        int hy = previewY + ui.pixelY(previewH, HUD_HEIGHT);
        rect(hx, hy, Math.min(HUD_WIDTH,previewW), Math.min(HUD_HEIGHT,previewH), 0xFF444C78);
        label("GhostC HUD", hx + 7, hy + 7, 0xFFFFFFFF);
        label(String.format(java.util.Locale.ROOT, "X %.1f%%   Y %.1f%%", ui.hudX()*100, ui.hudY()*100),
            previewX, previewY + previewH + 9, 0xFFBEC6D9);
    }
    @Override protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        rebuild();
        if (button != 0 || !inside(mx,my,x,y,w,h)) return;
        if (inside(mx,my,x+w-99,y+6,40,17)) {
            ui.layout(ui.layout() == UiState.Layout.WIDE ? UiState.Layout.COMPACT : UiState.Layout.WIDE);
            rebuild();
            host.persistUi(); return;
        }
        if (inside(mx,my,x+w-55,y+6,50,17)) {
            ui.theme(ui.theme() == UiState.Theme.INDIGO ? UiState.Theme.GRAPHITE : UiState.Theme.INDIGO);
            host.persistUi(); return;
        }
        if (inside(mx,my,x+side+8,y+3,70,24)) { hudPage = false; return; }
        if (inside(mx,my,x+side+84,y+3,47,24)) { hudPage = true; capture = null; return; }
        if (hudPage) {
            if (previewW<1 || previewH<1) return;
            int hx = previewX + ui.pixelX(previewW, HUD_WIDTH);
            int hy = previewY + ui.pixelY(previewH, HUD_HEIGHT);
            if (inside(mx,my,hx,hy,Math.min(HUD_WIDTH,previewW),Math.min(HUD_HEIGHT,previewH))) {
                hudDragging = true; dragX = mx - hx; dragY = my - hy;
            }
            return;
        }
        if (search != null) search.mouseClicked(mx,my,button);
        if (inside(mx,my,x+4,y+63,side-8,6*23)) {
            int idx = (my - (y+62)) / 23;
            if (idx >= 0 && idx <= 5) {
                ui.category(idx == 0 ? null : GhostModule.Category.valueOf(
                    new String[]{"ALL","COMBAT","MOVEMENT","WORLD","RENDER","UTILITY"}[idx]));
                scroll = 0;
            }
            return;
        }
        GuiLayout zone = GuiLayout.calculate(width,height,ui.layout());
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
                }
            }
            return;
        }
        ModuleRegistry.Entry e = host.modules().get(selected);
        int settingY = listY + listH + 5 + 22;
        if (inside(mx,my,x+side+11,settingY,w-side-26,18)) {
            capture = selected; message = ""; return;
        }
        List<Map.Entry<String, Setting<?>>> settings = new ArrayList<Map.Entry<String, Setting<?>>>(e.settings().entrySet());
        int index = GuiHitZones.settingIndex(zone,mx,my,settingScroll,settings.size());
        if (index >= 0) {
            Map.Entry<String, Setting<?>> it = settings.get(index);
            try {
                Setting<?> setting = it.getValue();
                if (setting instanceof Setting.Bool) host.uiActions().bool(selected, it.getKey());
                else if (setting instanceof Setting.Choice) host.uiActions().cycle(selected, it.getKey());
                else if (setting instanceof Setting.Number) {
                    int right = x+w-17;
                    int barWidth = Math.max(33,(w-side)/3);
                    if (inside(mx,my,right-barWidth,GuiHitZones.settingStartY(zone)+
                            (index-settingScroll)*22,barWidth,19))
                        host.uiActions().fraction(selected,it.getKey(),
                            (double)(mx-(right-barWidth))/barWidth);
                }
                message = "";
            } catch (RuntimeException ex) { message = "Setting rejected"; }
        }
    }
    @Override protected void mouseClickMove(int mx,int my,int button,long timeSinceLastClick) {
        if (hudDragging && button == 0 && previewW>0 && previewH>0) {
            ui.positionPixels(mx - previewX - dragX, my - previewY - dragY,
                previewW,previewH,HUD_WIDTH,HUD_HEIGHT);
        }
    }
    @Override protected void mouseReleased(int mx,int my,int state) {
        super.mouseReleased(mx,my,state);
        if (hudDragging) { hudDragging = false; host.persistUi(); }
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && !hudPage) {
            rebuild();
            GuiLayout zone=GuiLayout.calculate(width,height,ui.layout());
            int direction = wheel > 0 ? -1 : 1;
            int mx = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
            int my = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
            if (GuiHitZones.settingsArea(zone,mx,my)) {
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
        if (capture != null) {
            String id = capture; capture = null;
            if (keyCode == Keyboard.KEY_ESCAPE) return;
            if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) keyCode = 0;
            if (keyCode == Keyboard.KEY_F7) { message = "F7 reserved for ClickGUI"; return; }
            try { host.uiActions().bind(id,keyCode); message = ""; }
            catch (RuntimeException ex) { message = "Key already assigned or invalid"; }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_F7) {
            mc.displayGuiScreen(null); return;
        }
        if (!hudPage && search.textboxKeyTyped(character,keyCode)) {
            ui.query(search.getText()); scroll = 0; return;
        }
        super.keyTyped(character,keyCode);
    }
    @Override public void onGuiClosed() {
        host.diagnostics().record(Counter.GUI_CLOSE);
        Keyboard.enableRepeatEvents(false);
        hudDragging = false;
        capture = null;
        host.persistUi();
        host.inputFocus(true);
        super.onGuiClosed();
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
