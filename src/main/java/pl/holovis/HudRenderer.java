package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Elementy HUD: Watermark (nazwa, FPS, ping), Koordynaty, Spotify (kompaktowy pasek), TargetHUD i Cooldowns.
 * Pozycje (ułamki ekranu) i rozmiary są w configu.
 */
public final class HudRenderer {
    private HudRenderer() {}

    public enum Element {
        WATERMARK("Watermark"), COORDS("Koordynaty"), SPOTIFY("Spotify"), TARGET("TargetHUD"), COOLDOWNS("Cooldowns"), POTIONS("Potions"), ARMOR("Armor Status");
        public final String label;
        Element(String l) { label = l; }
    }

    public static boolean enabled(Element e) {
        HoloConfig c = HoloConfig.get();
        return switch (e) {
            case WATERMARK -> c.watermark;
            case COORDS -> c.coords;
            case SPOTIFY -> c.spotify || c.ytMusic;
            case TARGET -> c.targetHud;
            case COOLDOWNS -> c.cooldowns;
            case POTIONS -> c.potions;
            case ARMOR -> c.armorStatus;
        };
    }

    public static float[] pos(Element e) {
        HoloConfig c = HoloConfig.get();
        return switch (e) {
            case WATERMARK -> new float[]{c.wmX, c.wmY};
            case COORDS -> new float[]{c.coX, c.coY};
            case SPOTIFY -> new float[]{c.spX, c.spY};
            case TARGET -> new float[]{c.tgX, c.tgY};
            case COOLDOWNS -> new float[]{c.cdX, c.cdY};
            case POTIONS -> new float[]{c.poX, c.poY};
            case ARMOR -> new float[]{c.arX, c.arY};
        };
    }

    public static void setPos(Element e, float x, float y) {
        HoloConfig c = HoloConfig.get();
        switch (e) {
            case WATERMARK -> { c.wmX = x; c.wmY = y; }
            case COORDS -> { c.coX = x; c.coY = y; }
            case SPOTIFY -> { c.spX = x; c.spY = y; }
            case TARGET -> { c.tgX = x; c.tgY = y; }
            case COOLDOWNS -> { c.cdX = x; c.cdY = y; }
            case POTIONS -> { c.poX = x; c.poY = y; }
            case ARMOR -> { c.arX = x; c.arY = y; }
        }
    }

    public static void resetPositions() {
        HoloConfig c = HoloConfig.get();
        c.wmX = 0.01f; c.wmY = 0.02f;
        c.coX = 0.01f; c.coY = 0.055f;
        c.spX = 0.01f; c.spY = 0.09f;
        c.tgX = 0.55f; c.tgY = 0.55f;
        c.cdX = 0.01f; c.cdY = 0.30f;
        c.poX = 0.01f; c.poY = 0.45f;
        c.arX = 0.80f; c.arY = 0.88f;
        c.wmScale = c.coScale = c.spScale = c.spCardScale = c.tgScale = c.cdScale = c.poScale = c.arScale = 1f;
    }

    public static final float MIN_SCALE = 0.5f, MAX_SCALE = 3f;

    public static float scale(Element e) {
        HoloConfig c = HoloConfig.get();
        float s = switch (e) {
            case WATERMARK -> c.wmScale;
            case COORDS -> c.coScale;
            case SPOTIFY -> c.spScale;
            case TARGET -> c.tgScale;
            case COOLDOWNS -> c.cdScale;
            case POTIONS -> c.poScale;
            case ARMOR -> c.arScale;
        };
        return MathHelper.clamp(s, MIN_SCALE, MAX_SCALE);
    }

    public static void setScale(Element e, float s) {
        HoloConfig c = HoloConfig.get();
        s = MathHelper.clamp(s, MIN_SCALE, MAX_SCALE);
        switch (e) {
            case WATERMARK -> c.wmScale = s;
            case COORDS -> c.coScale = s;
            case SPOTIFY -> c.spScale = s;
            case TARGET -> c.tgScale = s;
            case COOLDOWNS -> c.cdScale = s;
            case POTIONS -> c.poScale = s;
            case ARMOR -> c.arScale = s;
        }
    }

    private static final int GAP = 7;

    /** Segment tekstu: kolorowy (akcent) albo zwykły. */
    private record Seg(String text, boolean accent) {}

    private static int ping() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return 0;
        PlayerListEntry en = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return en == null ? 0 : en.getLatency();
    }

    private static List<Seg> parts(Element e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HoloConfig cfg = HoloConfig.get();
        List<Seg> l = new ArrayList<>();
        if (e == Element.COORDS) {
            l.add(new Seg("XYZ", true));
            l.add(new Seg("|", false));
            l.add(new Seg(mc.player == null ? "0, 0, 0"
                    : mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ(), false));
            return l;
        }
        l.add(new Seg("Kosa", true));
        if (cfg.showPing) {
            l.add(new Seg("|", false));
            l.add(new Seg(ping() + " ms", false));
        }
        if (cfg.showFps) {
            l.add(new Seg("|", false));
            l.add(new Seg(mc.getCurrentFps() + " FPS", false));
        }
        return l;
    }

    private static int totalWidth(TextRenderer tr, List<Seg> p) {
        int w = 6;
        for (int i = 0; i < p.size(); i++) w += tr.getWidth(p.get(i).text()) + (i > 0 ? GAP : 0);
        return w;
    }

    // ---- Spotify: ciemna pigułka [ikona] nazwa piosenki [equalizer] ----
    private static final int SP_H = 20, SP_MAX_TEXT = 140;

    private static String spotifyText() {
        if (!Spotify.running) return (HoloConfig.get().ytMusic && !HoloConfig.get().spotify) ? "YT Music" : "Spotify";
        if (!Spotify.title.isEmpty()) return Spotify.title;
        return Spotify.track.isEmpty() ? "Pauza" : Spotify.track;
    }

    private static int spotifyWidth(TextRenderer tr) {
        String t = tr.trimToWidth(spotifyText(), SP_MAX_TEXT);
        return 6 + 14 + 6 + tr.getWidth(t) + 8 + 14 + 7;
    }

    private static void drawSpotify(DrawContext ctx, int x, int y) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HoloConfig cfg = HoloConfig.get();
        TextRenderer tr = mc.textRenderer;
        int c1 = cfg.spColor.rgb();
        int c2 = cfg.spGradient ? cfg.spColor2.rgb() : c1;
        String t = tr.trimToWidth(spotifyText(), SP_MAX_TEXT);
        int w = spotifyWidth(tr);

        Gfx.rrect(ctx, x - 1, y - 1, w + 2, SP_H + 2, 9, Gfx.argb(0xFFFFFF, 0.10f));
        Gfx.rrect(ctx, x, y, w, SP_H, 8, 0xF2101018);

        // ikona nutki w małym kwadracie
        int ix = x + 5, iy = y + 3;
        Gfx.rrect(ctx, ix, iy, 14, 14, 4, Spotify.fromBrowser ? Gfx.argb(0xFF2D2D, 0.55f) : Gfx.argb(c1, 0.22f));
        int nc = 0xFFE8EEF8;
        ctx.fill(ix + 5, iy + 3, ix + 6, iy + 10, nc);
        ctx.fill(ix + 10, iy + 2, ix + 11, iy + 9, nc);
        ctx.fill(ix + 5, iy + 3, ix + 11, iy + 5, nc);
        Gfx.circle(ctx, ix + 4, iy + 10, 2, nc);
        Gfx.circle(ctx, ix + 9, iy + 9, 2, nc);

        Gfx.text(ctx, tr, t, x + 6 + 14 + 6, y + 6, 0xFFE8EEF8, false);

        // equalizer po prawej (rusza się, gdy gra muzyka)
        int bx = x + w - 7 - 14;
        double tm = Util.getMeasuringTimeMs() / 1000.0;
        for (int i = 0; i < 4; i++) {
            int h = Spotify.playing ? 3 + (int) (Math.abs(Math.sin(tm * (3.1 + i * 0.7) + i * 1.9)) * 8) : 3;
            int col = 0xFF000000 | Gfx.mix(c1, c2, i / 3f);
            Gfx.rrect(ctx, bx + i * 4, y + SP_H - 4 - h, 2, h, 1, col);
        }
    }

    /** Rozmiar elementu w skali 1x. */
    public static int[] baseSize(Element e) {
        if (e == Element.TARGET) return TargetHud.size();
        if (e == Element.COOLDOWNS) return Cooldowns.size();
        if (e == Element.POTIONS) return PotionsHud.size();
        if (e == Element.ARMOR) return ArmorHud.size();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (e == Element.SPOTIFY) return new int[]{spotifyWidth(tr), SP_H};
        return new int[]{totalWidth(tr, parts(e)), 12};
    }

    /** Rozmiar elementu na ekranie (z uwzględnieniem skali). */
    public static int[] size(Element e) {
        int[] b = baseSize(e);
        float s = scale(e);
        return new int[]{Math.max(1, Math.round(b[0] * s)), Math.max(1, Math.round(b[1] * s))};
    }

    public static int[] pixelPos(Element e, int sw, int sh) {
        int[] s = size(e);
        float[] f = pos(e);
        return new int[]{
                MathHelper.clamp((int) (f[0] * sw), 0, Math.max(0, sw - s[0])),
                MathHelper.clamp((int) (f[1] * sh), 0, Math.max(0, sh - s[1]))};
    }

    /** preview = true w edytorze HUD. Rysuje w skali ustawionej dla elementu (x, y = lewy górny róg na ekranie). */
    public static void draw(DrawContext ctx, Element e, int x, int y, boolean preview) {
        float s = scale(e);
        if (Math.abs(s - 1f) < 0.005f) { drawBase(ctx, e, x, y, preview); return; }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0f);
        ctx.getMatrices().scale(s, s, 1f);
        drawBase(ctx, e, 0, 0, preview);
        ctx.getMatrices().pop();
    }

    private static void drawBase(DrawContext ctx, Element e, int x, int y, boolean preview) {
        if (e == Element.TARGET) { TargetHud.draw(ctx, x, y, preview); return; }
        if (e == Element.COOLDOWNS) { Cooldowns.draw(ctx, x, y, preview); return; }
        if (e == Element.POTIONS) { PotionsHud.draw(ctx, x, y, preview); return; }
        if (e == Element.ARMOR) { ArmorHud.draw(ctx, x, y, preview); return; }
        if (e == Element.SPOTIFY) { drawSpotify(ctx, x, y); return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        HoloConfig cfg = HoloConfig.get();
        TextRenderer tr = mc.textRenderer;
        int c1, c2;
        if (e == Element.COORDS) {
            c1 = cfg.coColor.rgb();
            c2 = c1;
        } else {
            c1 = cfg.hudColor.rgb();
            c2 = cfg.hudGradient ? cfg.hudColor2.rgb() : c1;
        }

        List<Seg> p = parts(e);
        int w = totalWidth(tr, p);
        if (cfg.hudBg) ctx.fill(x, y, x + w, y + 12, 0x80000000);
        int cx = x + 3;
        for (int i = 0; i < p.size(); i++) {
            Seg s = p.get(i);
            if (i > 0) cx += GAP;
            if (s.accent()) {
                Gfx.gradientText(ctx, tr, s.text(), cx, y + 2, c1, c2, 255, true);
            } else {
                int col = s.text().equals("|") ? 0xFF8B94A8 : 0xFFF2F5FA;
                ctx.drawText(tr, s.text(), cx, y + 2, col, true);
            }
            cx += tr.getWidth(s.text());
        }
    }

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        for (Element e : Element.values()) {
            if (!enabled(e)) continue;
            if (e == Element.TARGET && !TargetHud.visible()) continue;
            if (e == Element.COOLDOWNS && !Cooldowns.visible()) continue;
            if (e == Element.POTIONS && !PotionsHud.visible()) continue;
            if (e == Element.ARMOR && !ArmorHud.visible()) continue;
            int[] p = pixelPos(e, sw, sh);
            if (e == Element.TARGET) {
                int[] s = size(e);
                int[] tp = TargetHud.trackedPos(s[0], s[1], sw, sh);
                if (tp != null) p = tp;
            }
            draw(ctx, e, p[0], p[1], false);
        }
    }
}
