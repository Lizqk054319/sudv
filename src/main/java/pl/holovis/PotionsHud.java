package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Potions: lista aktywnych efektów (nazwa, poziom, czas) jako element HUD. */
public final class PotionsHud {
    private PotionsHud() {}

    private static final int ROW = 18, GAP = 2;

    private record Line(String name, String lvl, String time, int rgb, boolean low, RegistryEntry<StatusEffect> type) {}

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n < r.length ? r[n] : String.valueOf(n);
    }

    private static String time(int ticks) {
        int s = Math.max(0, ticks / 20);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    private static List<Line> lines(boolean preview) {
        List<Line> l = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            List<StatusEffectInstance> eff = new ArrayList<>(mc.player.getStatusEffects());
            eff.sort(Comparator.comparingInt(StatusEffectInstance::getDuration).reversed());
            for (StatusEffectInstance e : eff) {
                String name = e.getEffectType().value().getName().getString();
                int lvl = e.getAmplifier() + 1;
                boolean inf = e.isInfinite();
                l.add(new Line(name, lvl > 1 ? roman(lvl) : "", inf ? "\u221E" : time(e.getDuration()),
                        e.getEffectType().value().getColor() & 0xFFFFFF, !inf && e.getDuration() < 200, e.getEffectType()));
            }
        }
        if (l.isEmpty() && preview) {
            l.add(new Line("Speed", "II", "3:00", 0x33EBFF, false, null));
            l.add(new Line("Strength", "", "1:24", 0xFFC700, false, null));
            l.add(new Line("Regeneration", "", "0:08", 0xCD5CAB, true, null));
        }
        return l;
    }

    public static boolean visible() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && !mc.player.getStatusEffects().isEmpty();
    }

    private static int lineWidth(TextRenderer tr, Line ln) {
        int w = 4 + 14 + 6 + tr.getWidth(ln.name());
        if (!ln.lvl().isEmpty()) w += 4 + tr.getWidth(ln.lvl());
        return w + 12 + tr.getWidth(ln.time()) + 7;
    }

    public static int[] size() {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        List<Line> l = lines(true);
        int w = 90;
        for (Line ln : l) w = Math.max(w, lineWidth(tr, ln));
        int n = Math.max(1, l.size());
        return new int[]{w, n * ROW + (n - 1) * GAP};
    }

    public static void draw(DrawContext ctx, int x, int y, boolean preview) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HoloConfig cfg = HoloConfig.get();
        TextRenderer tr = mc.textRenderer;
        List<Line> l = lines(preview);
        int w = size()[0];
        int tc = 0xFF000000 | cfg.poColor.rgb();
        for (int i = 0; i < l.size(); i++) {
            Line ln = l.get(i);
            int ry = y + i * (ROW + GAP);
            int col = ln.rgb();
            // ciemna "pigułka" z poświatą w kolorze efektu
            Gfx.rrect(ctx, x, ry, w, ROW, 5, 0xB00B0E15);
            Gfx.rboxH(ctx, x, ry, Math.min(w, 60), ROW, 5, Gfx.argb(col, 0.28f), Gfx.argb(col, 0f));
            Gfx.rrect(ctx, x, ry + 3, 2, ROW - 6, 1, 0xFF000000 | col);
            // ikona efektu z Minecrafta
            int ix = x + 4, iy = ry + 2;
            boolean icon = false;
            if (ln.type() != null) {
                try {
                    Sprite sp = mc.getStatusEffectSpriteManager().getSprite(ln.type());
                    ctx.drawSpriteStretched(RenderLayer::getGuiTextured, sp, ix, iy, 14, 14);
                    icon = true;
                } catch (Exception ignored) {}
            }
            if (!icon) {
                Gfx.circle(ctx, ix + 7, iy + 7, 5, 0xFF000000 | col);
                Gfx.circle(ctx, ix + 7, iy + 7, 2, 0x88FFFFFF);
            }
            int tx = ix + 14 + 6;
            ctx.drawText(tr, ln.name(), tx, ry + 5, tc, true);
            tx += tr.getWidth(ln.name());
            if (!ln.lvl().isEmpty()) {
                ctx.drawText(tr, ln.lvl(), tx + 4, ry + 5, 0xFF000000 | Gfx.mix(col, 0xFFFFFF, 0.35f), true);
            }
            String t = ln.time();
            boolean blink = ln.low() && (System.currentTimeMillis() / 350) % 2 == 0;
            int tcol = ln.low() ? (blink ? 0xFFFF5A5A : 0xFFFFB0B0) : 0xFFAAB4C8;
            ctx.drawText(tr, t, x + w - tr.getWidth(t) - 6, ry + 5, tcol, true);
        }
    }
}
