package pl.holovis.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import pl.holovis.CustomNametag;
import pl.holovis.Gfx;
import pl.holovis.HoloConfig;
import pl.holovis.PostFx;

/** Ustawienia NameTag: włącz, pierwsza i druga linia, rozmiar, tryb, czcionka, ikonka (okno wyboru). */
public class NametagScreen extends Screen {
    private static final int W = 300, H = 340, ROW = 30;
    private float sc = 1f;
    private int vw, vh;
    private int focus = 0;   // 1 linia 1, 2 linia 2
    private boolean dragSize, dragHeight, picker;
    private String search = "";
    private static int colorIdx = 0;
    private final Screen parent;

    public NametagScreen(Screen parent) { super(Text.literal("NameTag")); this.parent = parent; }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { HoloConfig.save(); if (client != null) client.setScreen(parent); }

    @Override
    protected void init() {
        sc = MathHelper.clamp(Math.min((width - 12f) / W, (height - 12f) / H), 0.5f, 1.2f);
        vw = (int) (width / sc);
        vh = (int) (height / sc);
    }

    private int ox() { return (vw - W) / 2; }
    private int oy() { return (vh - H) / 2; }
    private boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private void t(DrawContext c, String s, int x, int y, int col) { Gfx.text(c, textRenderer, s, x, y, 0xFF000000 | col, false); }

    private void box(DrawContext c, int x, int y, int w, int h, boolean hov) {
        Gfx.rrect(c, x, y, w, h, 5, hov ? 0xFF1E2430 : 0xFF151922);
    }

    @Override
    public void render(DrawContext c, int rawMx, int rawMy, float delta) {
        HoloConfig cfg = HoloConfig.get();
        int accent = cfg.guiColor.rgb();
        PostFx.blurBackground(c, cfg.clientBlur);
        c.fill(0, 0, width, height, 0x55000000);
        c.getMatrices().push();
        c.getMatrices().scale(sc, sc, 1f);
        int mx = (int) (rawMx / sc), my = (int) (rawMy / sc);
        int x = ox(), y = oy();

        Gfx.rrect(c, x, y, W, H, 12, 0xF51A1C22);
        String title = "Nametag";
        t(c, title, x + (W - textRenderer.getWidth(title)) / 2, y + 12, 0xFFFFFF);
        c.fill(x + 8, y + 28, x + W - 8, y + 29, 0x30FFFFFF);

        int cx = x + W - 128, cw = 116;
        String[] labels = {"Nametag", "First Line", "Second Line", "Size", "Height", "Mode", "Font", "Kolor", "Logo"};
        for (int i = 0; i < labels.length; i++) {
            int ry = y + 38 + i * ROW;
            t(c, labels[i], x + 14, ry + 8, 0xE8EEF8);
            boolean hov = in(mx, my, cx, ry, cw, 24);
            switch (i) {
                case 0 -> { box(c, cx + cw - 56, ry, 56, 24, hov); String s = cfg.ntOn ? "ON" : "OFF"; t(c, s, cx + cw - 28 - textRenderer.getWidth(s) / 2, ry + 8, cfg.ntOn ? 0x5CE08A : 0xFF6B7A); }
                case 1, 2 -> {
                    box(c, cx, ry, cw, 24, hov);
                    String v = i == 1 ? cfg.ntLine1 : cfg.ntLine2;
                    boolean f = focus == i;
                    String sh = v + (f && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
                    if (textRenderer.getWidth(sh) > cw - 12) sh = textRenderer.trimToWidth(sh, cw - 12, true);
                    t(c, sh, cx + 6, ry + 8, 0xFFFFFF);
                    if (f) c.drawBorder(cx, ry, cw, 24, Gfx.argb(accent, 0.8f));
                }
                case 3 -> {
                    box(c, cx, ry, cw, 24, hov);
                    float f = (cfg.ntSize - 1f) / 4f;
                    int sx = cx + 8, sw = cw - 34;
                    Gfx.rrect(c, sx, ry + 11, sw, 3, 1, 0x40FFFFFF);
                    Gfx.rrect(c, sx + (int) (sw * f) - 2, ry + 5, 5, 14, 2, 0xFFFFFFFF);
                    String v = String.valueOf(Math.round(cfg.ntSize));
                    t(c, v, cx + cw - 14, ry + 8, 0xFFFFFF);
                }
                case 4 -> {
                    box(c, cx, ry, cw, 24, hov);
                    float f = (cfg.ntHeight - 0.2f) / 2.3f;
                    int sx = cx + 8, sw = cw - 40;
                    Gfx.rrect(c, sx, ry + 11, sw, 3, 1, 0x40FFFFFF);
                    Gfx.rrect(c, sx + (int) (sw * f) - 2, ry + 5, 5, 14, 2, 0xFFFFFFFF);
                    String v = String.format(java.util.Locale.ROOT, "%.1f", cfg.ntHeight);
                    t(c, v, cx + cw - 6 - textRenderer.getWidth(v), ry + 8, 0xFFFFFF);
                }
                case 5 -> { box(c, cx + 16, ry, cw - 16, 24, hov); String s = cfg.ntMode == 0 ? "SINGLE" : "DOUBLE"; t(c, s, cx + 16 + (cw - 16 - textRenderer.getWidth(s)) / 2, ry + 8, 0xFFFFFF); }
                case 6 -> { box(c, cx + 16, ry, cw - 16, 24, hov); String s = CustomNametag.FONTS[MathHelper.clamp(cfg.ntFont, 0, 2)]; t(c, s, cx + 16 + (cw - 16 - textRenderer.getWidth(s)) / 2, ry + 8, 0xFFFFFF); }
                case 7 -> { box(c, cx + 16, ry, cw - 16, 24, hov); Gfx.rrect(c, cx + 22, ry + 5, cw - 28, 14, 4, 0xFF000000 | cfg.ntColor.rgb()); }
                default -> {
                    box(c, cx + 16, ry, cw - 16, 24, hov);
                    String s = cfg.ntIcon < 0 ? "Wybierz" : CustomNametag.ICONS[Math.min(cfg.ntIcon, CustomNametag.CUSTOM)];
                    Text tx = cfg.ntIcon < 0 ? Text.literal(s) : CustomNametag.line(s, cfg.ntIcon, 0, 0xFFFFFF);
                    int tw = textRenderer.getWidth(tx) + (cfg.ntIcon == CustomNametag.CUSTOM ? 11 : 0);
                    int tx0 = cx + 16 + (cw - 16 - tw) / 2;
                    if (cfg.ntIcon == CustomNametag.CUSTOM) { customIcon(c, tx0, ry + 7, 9); tx0 += 11; }
                    c.drawText(textRenderer, tx, tx0, ry + 8, 0xFFFFFFFF, false);
                }
            }
        }
        // podgląd
        int py = y + H - 36;
        Gfx.rrect(c, x + 10, py, W - 20, 26, 6, 0x40000000);
        Text prev = CustomNametag.line(cfg.ntLine1.isEmpty() ? "Twój napis" : cfg.ntLine1, cfg.ntIcon, cfg.ntFont, cfg.ntColor.rgb());
        int pw2 = textRenderer.getWidth(prev) + (cfg.ntIcon == CustomNametag.CUSTOM ? 11 : 0);
        int px0 = x + (W - pw2) / 2;
        if (cfg.ntIcon == CustomNametag.CUSTOM) { customIcon(c, px0, py + 8, 9); px0 += 11; }
        c.drawText(textRenderer, prev, px0, py + 9, 0xFFFFFFFF, true);
        if (cfg.ntIcon == CustomNametag.CUSTOM && !CustomNametag.selfError.isEmpty())
            t(c, CustomNametag.selfError, x + 12, py - 12, 0xFF6B7A);

        if (picker) drawPicker(c, mx, my, accent);
        c.getMatrices().pop();
    }

    /** Rysuje własny obrazek (config/holovis/nametag_icon.png) albo pusty kwadrat. */
    private void customIcon(DrawContext c, int x, int y, int s) {
        net.minecraft.util.Identifier id = CustomNametag.texture(CustomNametag.selfImage());
        if (id != null) c.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, id, x, y, 0f, 0f, s, s, s, s);
        else c.drawBorder(x, y, s, s, 0xFF8A93A6);
    }

    private void drawPicker(DrawContext c, int mx, int my, int accent) {
        int pw = 260, ph = 214, px = (vw - pw) / 2, py = (vh - ph) / 2;
        c.fill(0, 0, vw, vh, 0x80000000);
        Gfx.rrect(c, px, py, pw, ph, 10, 0xFA1A1C22);
        Gfx.rrect(c, px + 8, py + 8, pw - 44, 18, 4, 0xFF101217);
        t(c, search.isEmpty() ? "Szukaj..." : search + "_", px + 14, py + 13, search.isEmpty() ? 0x5E6678 : 0xFFFFFF);
        t(c, "✕", px + pw - 24, py + 13, 0xFFFFFF);
        int i2 = 0;
        for (int i = -1; i < CustomNametag.ICONS.length; i++) {
            String name = i < 0 ? "Brak" : CustomNametag.ICONS[i];
            if (!search.isEmpty() && !name.toLowerCase().contains(search.toLowerCase())) continue;
            int col = i2 % 4, row = i2 / 4;
            int bx = px + 12 + col * 60, by = py + 34 + row * 52;
            boolean hov = in(mx, my, bx, by, 56, 48);
            Gfx.rrect(c, bx, by, 56, 48, 6, hov ? Gfx.argb(accent, 0.3f) : 0x20FFFFFF);
            c.getMatrices().push();
            c.getMatrices().translate(bx + 28, by + 6, 0f);
            c.getMatrices().scale(2.4f, 2.4f, 1f);
            if (i == CustomNametag.CUSTOM) {
                customIcon(c, -5, 0, 10);
            } else {
                Text ic = i < 0 ? Text.literal("∅") : CustomNametag.line("", i, 0, 0xFFFFFF);
                c.drawText(textRenderer, ic, -textRenderer.getWidth(ic) / 2, 0, 0xFFFFFFFF, false);
            }
            c.getMatrices().pop();
            t(c, name, bx + (56 - textRenderer.getWidth(name)) / 2, by + 36, 0xE8EEF8);
            i2++;
        }
    }

    private void setSize(double mx) {
        int cx = ox() + W - 128 + 8, sw = 116 - 34;
        HoloConfig.get().ntSize = 1f + 4f * MathHelper.clamp((float) (mx - cx) / sw, 0f, 1f);
    }

    @Override
    public boolean mouseClicked(double rawX, double rawY, int button) {
        double mx = rawX / sc, my = rawY / sc;
        HoloConfig cfg = HoloConfig.get();
        if (picker) {
            int pw = 260, ph = 214, px = (vw - pw) / 2, py = (vh - ph) / 2;
            if (in(mx, my, px + pw - 30, py + 6, 24, 22) || !in(mx, my, px, py, pw, ph)) { picker = false; return true; }
            int i2 = 0;
            for (int i = -1; i < CustomNametag.ICONS.length; i++) {
                String name = i < 0 ? "Brak" : CustomNametag.ICONS[i];
                if (!search.isEmpty() && !name.toLowerCase().contains(search.toLowerCase())) continue;
                int bx = px + 12 + (i2 % 4) * 60, by = py + 34 + (i2 / 4) * 52;
                if (in(mx, my, bx, by, 56, 48)) {
                    cfg.ntIcon = i;
                    picker = false;
                    if (i == CustomNametag.CUSTOM) {
                        CustomNametag.refreshSelf();
                        if (!java.nio.file.Files.exists(CustomNametag.ICON_FILE)) {
                            try { java.nio.file.Files.createDirectories(CustomNametag.ICON_FILE.getParent()); } catch (Exception ignored) {}
                            net.minecraft.util.Util.getOperatingSystem().open(CustomNametag.ICON_FILE.getParent().toFile());
                        }
                    }
                    return true;
                }
                i2++;
            }
            return true;
        }
        int x = ox(), y = oy();
        if (!in(mx, my, x, y, W, H)) { close(); return true; }
        int cx = x + W - 128, cw = 116;
        focus = 0;
        for (int i = 0; i < 9; i++) {
            int ry = y + 38 + i * ROW;
            if (!in(mx, my, cx, ry, cw, 24)) continue;
            switch (i) {
                case 0 -> cfg.ntOn = !cfg.ntOn;
                case 1, 2 -> focus = i;
                case 3 -> { dragSize = true; setSize(mx); }
                case 4 -> { dragHeight = true; setHeight(mx); }
                case 5 -> cfg.ntMode = 1 - cfg.ntMode;
                case 6 -> cfg.ntFont = (cfg.ntFont + 1) % 3;
                case 7 -> { int[] pal = {0xFFFFFF, 0xFFD23C, 0xFF7AC8, 0xB44CFF, 0x00E5FF, 0x5CE08A, 0xFF5A5A};
                    colorIdx = (colorIdx + 1) % pal.length; cfg.ntColor.rainbow = false; cfg.ntColor.set(pal[colorIdx]); }
                default -> { picker = true; search = ""; }
            }
            return true;
        }
        return true;
    }

    private void setHeight(double mx) {
        int cx = ox() + W - 128 + 8, sw = 116 - 40;
        HoloConfig.get().ntHeight = 0.2f + 2.3f * MathHelper.clamp((float) (mx - cx) / sw, 0f, 1f);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragSize) { setSize(mx / sc); return true; }
        if (dragHeight) { setHeight(mx / sc); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) { dragSize = false; dragHeight = false; return super.mouseReleased(mx, my, button); }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (ch < 32) return false;
        HoloConfig cfg = HoloConfig.get();
        if (picker) { if (search.length() < 20) search += ch; return true; }
        if (focus == 1 && cfg.ntLine1.length() < 32) { cfg.ntLine1 += ch; return true; }
        if (focus == 2 && cfg.ntLine2.length() < 32) { cfg.ntLine2 += ch; return true; }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        HoloConfig cfg = HoloConfig.get();
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (picker) { if (!search.isEmpty()) search = search.substring(0, search.length() - 1); }
            else if (focus == 1 && !cfg.ntLine1.isEmpty()) cfg.ntLine1 = cfg.ntLine1.substring(0, cfg.ntLine1.length() - 1);
            else if (focus == 2 && !cfg.ntLine2.isEmpty()) cfg.ntLine2 = cfg.ntLine2.substring(0, cfg.ntLine2.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && picker) { picker = false; return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { focus = 0; return true; }
        return super.keyPressed(key, scan, mods);
    }
}
