package pl.holovis.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import pl.holovis.Gfx;
import pl.holovis.HoloConfig;
import pl.holovis.Optimizer;
import pl.holovis.PostFx;

import java.util.List;

/** Okno /kosa optymalizacja: poziom (Medium / Hard / Ultra), podgląd zmian, Optymalizuj i Przywróć. */
public class OptimizeScreen extends Screen {
    private static final int W = 320, H = 300;
    private static final int GOOD = 0x5CE08A;

    private float sc = 1f;
    private int vw, vh;
    private int level = 0;
    private boolean priority = true, kosaFx = true;
    private String status = "";
    private List<String> preview = List.of();

    public OptimizeScreen() { super(Text.literal("Optymalizacja")); }

    @Override public boolean shouldPause() { return false; }

    @Override
    protected void init() {
        sc = MathHelper.clamp(Math.min((width - 12f) / W, (height - 12f) / H), 0.5f, 1f);
        vw = (int) (width / sc);
        vh = (int) (height / sc);
        refresh();
    }

    private void refresh() { preview = Optimizer.run(level, false, kosaFx); }

    private int ox() { return (vw - W) / 2; }
    private int oy() { return (vh - H) / 2; }

    private boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private void t(DrawContext c, String s, int x, int y, int rgb) { Gfx.text(c, textRenderer, s, x, y, 0xFF000000 | rgb, false); }

    private void button(DrawContext c, int x, int y, int w, int h, String label, int bg, boolean hov, boolean enabled) {
        Gfx.rrect(c, x, y, w, h, 7, Gfx.argb(bg, enabled ? (hov ? 0.95f : 0.75f) : 0.25f));
        t(c, label, x + (w - textRenderer.getWidth(label)) / 2, y + (h - 8) / 2, enabled ? 0xFFFFFF : 0x8890A0);
    }

    private void toggle(DrawContext c, int x, int y, String label, boolean on, int accent) {
        Gfx.rrect(c, x, y, 22, 12, 6, Gfx.argb(on ? accent : 0x3A4254, 0.95f));
        Gfx.circle(c, on ? x + 16 : x + 6, y + 6, 4, 0xFFFFFFFF);
        t(c, label, x + 28, y + 2, 0xE8EEF8);
    }

    @Override
    public void render(DrawContext c, int rawMx, int rawMy, float delta) {
        HoloConfig cfg = HoloConfig.get();
        int accent = cfg.guiColor.rgb();
        PostFx.blurBackground(c, cfg.clientBlur);
        c.fill(0, 0, width, height, 0x66000000);

        c.getMatrices().push();
        c.getMatrices().scale(sc, sc, 1f);
        int mx = (int) (rawMx / sc), my = (int) (rawMy / sc);
        int x = ox(), y = oy();

        Gfx.rrect(c, x - 1, y - 1, W + 2, H + 2, 13, Gfx.argb(0xFFFFFF, 0.10f));
        Gfx.rrect(c, x, y, W, H, 12, Gfx.argb(0x10141D, 0.96f));
        Gfx.rboxV(c, x, y, W, 46, 12, Gfx.argb(accent, 0.12f), Gfx.argb(accent, 0f));

        c.getMatrices().push();
        c.getMatrices().translate(x + 18, y + 14, 0f);
        c.getMatrices().scale(1.5f, 1.5f, 1f);
        t(c, "Optymalizacja", 0, 0, 0xFFFFFF);
        c.getMatrices().pop();
        String fps = client == null ? "" : client.getCurrentFps() + " FPS";
        t(c, fps, x + W - 18 - textRenderer.getWidth(fps), y + 18, GOOD);

        // poziomy
        int lw = (W - 36 - 18) / 4;
        for (int i = 0; i < 4; i++) {
            int bx = x + 18 + i * (lw + 6), by = y + 46;
            boolean sel = i == level;
            Gfx.rrect(c, bx, by, lw, 22, 8, Gfx.argb(sel ? accent : 0x262D3C, sel ? 0.9f : (in(mx, my, bx, by, lw, 22) ? 0.9f : 0.6f)));
            String s = Optimizer.LEVELS[i];
            t(c, s, bx + (lw - textRenderer.getWidth(s)) / 2, by + 7, 0xFFFFFF);
        }
        toggle(c, x + 18, y + 76, "Wyłącz ciężkie efekty KosaVisuals", kosaFx, accent);
        if (Optimizer.isWindows()) toggle(c, x + 18, y + 94, "Wysoki priorytet Minecrafta (Windows)", priority, accent);

        // podgląd zmian
        int ly = y + 116;
        Gfx.rrect(c, x + 14, ly, W - 28, 128, 8, Gfx.argb(0x000000, 0.25f));
        if (preview.isEmpty()) {
            t(c, "Nic do zmiany - ustawienia już są lekkie.", x + 22, ly + 8, 0x9AA4B8);
        } else {
            t(c, "Znaleziono " + preview.size() + " ustawień zabierających FPS:", x + 22, ly + 7, 0xC8D0E0);
            for (int i = 0; i < Math.min(10, preview.size()); i++)
                t(c, textRenderer.trimToWidth("• " + preview.get(i), W - 46), x + 22, ly + 20 + i * 10, 0x9AA4B8);
            if (preview.size() > 10) t(c, "... i " + (preview.size() - 10) + " więcej", x + 22, ly + 120 - 2, 0x6B7488);
        }

        int by = y + H - 46;
        int bw = (W - 36 - 8) / 2;
        button(c, x + 18, by, bw, 24, "Optymalizuj", accent, in(mx, my, x + 18, by, bw, 24), true);
        button(c, x + 26 + bw, by, bw, 24, "Przywróć", 0x5A3340, in(mx, my, x + 26 + bw, by, bw, 24), Optimizer.canRestore());
        if (!status.isEmpty()) t(c, textRenderer.trimToWidth(status, W - 36), x + 18, y + H - 16, GOOD);
        c.getMatrices().pop();
    }

    @Override
    public boolean mouseClicked(double rawX, double rawY, int button) {
        double mx = rawX / sc, my = rawY / sc;
        int x = ox(), y = oy();
        if (!in(mx, my, x, y, W, H)) { close(); return true; }
        int lw = (W - 36 - 18) / 4;
        for (int i = 0; i < 4; i++) {
            if (in(mx, my, x + 18 + i * (lw + 6), y + 46, lw, 22)) { level = i; refresh(); status = ""; return true; }
        }
        if (in(mx, my, x + 18, y + 74, 200, 16)) { kosaFx = !kosaFx; refresh(); return true; }
        if (Optimizer.isWindows() && in(mx, my, x + 18, y + 92, 220, 16)) { priority = !priority; return true; }
        int by = y + H - 46;
        int bw = (W - 36 - 8) / 2;
        if (in(mx, my, x + 18, by, bw, 24)) {
            List<String> done = Optimizer.run(level, true, kosaFx);
            if (priority) Optimizer.highPriority();
            status = done.isEmpty() ? "Nic do zmiany." : "Zmieniono " + done.size() + " ustawień (" + Optimizer.LEVELS[level] + ").";
            refresh();
            return true;
        }
        if (Optimizer.canRestore() && in(mx, my, x + 26 + bw, by, bw, 24)) {
            int n = Optimizer.restore();
            status = "Przywrócono " + n + " ustawień sprzed optymalizacji.";
            refresh();
            return true;
        }
        return super.mouseClicked(rawX, rawY, button);
    }
}
