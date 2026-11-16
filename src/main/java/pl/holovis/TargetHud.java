package pl.holovis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TargetHud: karta celu (głowa, nick, odległość, serca, pasek życia, zbroja i broń).
 * Zostaje widoczna po uderzeniu, nawet gdy się odwrócisz. Opcja Track player: karta wisi nad graczem.
 */
public final class TargetHud {
    private TargetHud() {}

    public static final int W = 172;
    private static final long LOOK_MS = 2500, HIT_MS = 10000;

    private static LivingEntity target, lastDrawn;
    private static long lastSeen, lastHit;
    private static float appear = 0f, shown = 0f, trail = 0f;
    private static long lastNs = System.nanoTime();

    // Track player: pozycja głowy celu na ekranie (współrzędne GUI), liczona przy renderze świata
    private static float trackX, trackY;
    private static long trackFrame;

    public static void tick(MinecraftClient mc) {
        if (!HoloConfig.get().targetHud || mc.player == null) { target = null; return; }
        HitResult hr = mc.crosshairTarget;
        if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof PlayerEntity p && p != mc.player) {
            if (p != target) lastHit = 0;
            target = p;
            lastSeen = System.currentTimeMillis();
        }
    }

    /** Po uderzeniu gracza HUD zostaje przez 10 s, także gdy już na niego nie patrzymy (np. odlot elytrą). */
    public static void notifyAttack(LivingEntity e) {
        if (e instanceof PlayerEntity) {
            target = e;
            long now = System.currentTimeMillis();
            lastSeen = now;
            lastHit = now;
        }
    }

    private static LivingEntity current() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!HoloConfig.get().targetHud || target == null || target.isRemoved() || mc.player == null) return null;
        if (target.getWorld() != mc.player.getWorld()) return null;
        long now = System.currentTimeMillis();
        boolean looked = now - lastSeen <= LOOK_MS;
        boolean fighting = now - lastHit <= HIT_MS && target.squaredDistanceTo(mc.player) < 64 * 64;
        if (!looked && !fighting) return null;
        return target;
    }

    /** Aktualny cel (do Target ESP) albo null. */
    public static LivingEntity currentTarget() { return current(); }

    public static boolean visible() { return current() != null || appear > 0.02f; }

    public static int[] size() { return new int[]{W, HoloConfig.get().targetArmor ? 58 : 44}; }

    private static String fmt(float v) { return String.format(Locale.ROOT, "%.1f", v).replace('.', ','); }

    // ------------------------------------------------------------------ Track player
    /** Wywoływane po renderze encji: rzutuje punkt nad głową celu na ekran. */
    public static void project(WorldRenderContext ctx) {
        HoloConfig cfg = HoloConfig.get();
        if (!cfg.targetHud || !cfg.tgTrack) return;
        LivingEntity t = current();
        if (t == null) t = appear > 0.04f ? lastDrawn : null;
        if (t == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d cam = ctx.camera().getPos();
        Vec3d lp = t.getLerpedPos(ctx.tickCounter().getTickDelta(false));
        Vector4f v = new Vector4f((float) (lp.x - cam.x), (float) (lp.y - cam.y + t.getHeight() + 0.45f), (float) (lp.z - cam.z), 1f);
        Matrix4f view = ctx.positionMatrix(), proj = ctx.projectionMatrix();
        if (view != null) view.transform(v);
        if (proj != null) proj.transform(v);
        if (v.w <= 0.05f) return;   // za kamerą
        float nx = v.x / v.w, ny = v.y / v.w;
        if (Math.abs(nx) > 1.4f || Math.abs(ny) > 1.4f) return;
        trackX = (nx * 0.5f + 0.5f) * mc.getWindow().getScaledWidth();
        trackY = (0.5f - ny * 0.5f) * mc.getWindow().getScaledHeight();
        trackFrame = System.nanoTime();
    }

    /** Pozycja karty przy śledzeniu gracza (lewy górny róg) albo null, gdy cel jest poza ekranem. */
    public static int[] trackedPos(int w, int h, int sw, int sh) {
        if (!HoloConfig.get().tgTrack || System.nanoTime() - trackFrame > 150_000_000L) return null;
        int x = MathHelper.clamp(Math.round(trackX - w / 2f), 0, Math.max(0, sw - w));
        int y = MathHelper.clamp(Math.round(trackY - h), 0, Math.max(0, sh - h));
        return new int[]{x, y};
    }

    // ------------------------------------------------------------------ rysowanie
    private static List<ItemStack> gear(LivingEntity e) {
        List<ItemStack> l = new ArrayList<>();
        if (!e.getMainHandStack().isEmpty()) l.add(e.getMainHandStack());
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot s : slots) {
            ItemStack st = e.getEquippedStack(s);
            if (!st.isEmpty()) l.add(st);
        }
        if (!e.getOffHandStack().isEmpty()) l.add(e.getOffHandStack());
        return l;
    }

    /** preview = true w edytorze HUD (atrapa gracza). */
    public static void draw(DrawContext ctx, int x, int y, boolean preview) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HoloConfig cfg = HoloConfig.get();
        TextRenderer tr = mc.textRenderer;

        long ns = System.nanoTime();
        float dt = Math.min(0.1f, (ns - lastNs) / 1_000_000_000f);
        lastNs = ns;

        LivingEntity t = preview ? null : current();
        float goal = (preview || t != null) ? 1f : 0f;
        appear += (goal - appear) * Math.min(1f, dt * 10f);
        if (preview) appear = 1f;
        if (appear < 0.04f) return;

        float hp, max, dist;
        String name;
        List<ItemStack> items;
        LivingEntity drawn;
        if (t != null || (!preview && lastDrawn != null)) {
            drawn = t != null ? t : lastDrawn;
            hp = Math.max(0f, drawn.getHealth());
            max = Math.max(1f, drawn.getMaxHealth());
            name = drawn.getName().getString();
            items = gear(drawn);
            dist = mc.player == null ? 0f : mc.player.distanceTo(drawn);
        } else if (preview) {
            drawn = mc.player;
            hp = 15f; max = 20f; name = "Gracz"; dist = 3.4f;
            items = List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_HELMET),
                    new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.IRON_LEGGINGS));
        } else {
            return;
        }
        if (preview || (t != null && t != lastDrawn)) { shown = hp; trail = hp; }
        if (t != null) lastDrawn = t;
        shown += (hp - shown) * Math.min(1f, dt * 9f);
        if (hp >= trail) trail = hp; else trail = Math.max(hp, trail - dt * max * 0.30f);

        float a = appear;
        int H = size()[1];
        y += (int) ((1f - a) * 8);
        int c1 = cfg.tgColor.rgb();
        int c2 = cfg.tgGradient ? cfg.tgColor2.rgb() : c1;

        // cień, obwódka z gradientem, ciemne szkło
        Gfx.rrect(ctx, x - 2, y, W + 4, H + 3, 11, Gfx.argb(0x000000, 0.28f * a));
        Gfx.rboxH(ctx, x - 1, y - 1, W + 2, H + 2, 10, Gfx.argb(c1, 0.75f * a), Gfx.argb(c2, 0.75f * a));
        Gfx.rrect(ctx, x, y, W, H, 9, Gfx.argb(0x0A0D14, 0.94f * a));
        Gfx.rboxV(ctx, x, y, W, 22, 9, Gfx.argb(c1, 0.13f * a), Gfx.argb(c1, 0f));

        // głowa z obramówką; czerwone mignięcie przy obrażeniach
        int hx = x + 6, hy = y + 6, hs = 32;
        Gfx.rboxV(ctx, hx - 1, hy - 1, hs + 2, hs + 2, 5, Gfx.argb(c1, a), Gfx.argb(c2, a));
        if (drawn instanceof AbstractClientPlayerEntity pl) {
            PlayerSkinDrawer.draw(ctx, pl.getSkinTextures(), hx, hy, hs);
        } else {
            Gfx.rrect(ctx, hx, hy, hs, hs, 4, Gfx.argb(0x2A3246, a));
        }
        if (drawn != null && drawn.hurtTime > 0 && !preview) {
            ctx.fill(hx, hy, hx + hs, hy + hs, Gfx.argb(0xFF2A2A, 0.45f * drawn.hurtTime / 10f * a));
        }

        int rx = hx + hs + 8, rr = x + W - 8;

        // życie po prawej
        String val;
        int valW;
        if (cfg.targetHearts) {
            val = fmt(shown / 2f);
            Gfx.heart(ctx, rr - 7, y + 7, Gfx.argb(0xFF4D5E, a));
            valW = tr.getWidth(val) + 11;
            Gfx.text(ctx, tr, val, rr - valW, y + 7, Gfx.argb(0xFFFFFF, a), true);
        } else {
            val = fmt(shown) + " HP";
            valW = tr.getWidth(val);
            Gfx.text(ctx, tr, val, rr - valW, y + 7, Gfx.argb(0xFFFFFF, a), true);
        }
        Gfx.text(ctx, tr, tr.trimToWidth(name, Math.max(20, rr - rx - valW - 6)), rx, y + 7, Gfx.argb(0xFFFFFF, a), true);

        // odległość i przewaga życia
        String sub = String.format(Locale.ROOT, "%.1f m", dist).replace('.', ',');
        Gfx.text(ctx, tr, sub, rx, y + 18, Gfx.argb(0x9AA4B8, a), false);
        if (!preview && mc.player != null && drawn != mc.player) {
            float diff = (mc.player.getHealth() - hp) / 2f;
            String d = (diff >= 0 ? "+" : "") + fmt(diff);
            int col = diff > 0.05f ? 0x5CFF8A : (diff < -0.05f ? 0xFF5C6E : 0xC8D0E0);
            Gfx.text(ctx, tr, d, rr - tr.getWidth(d), y + 18, Gfx.argb(col, a), false);
        }

        // pasek życia z "śladem" ostatnich obrażeń i połyskiem
        int bx = rx, by = y + 30, bw = rr - rx, bh = 7;
        Gfx.rrect(ctx, bx, by, bw, bh, 3, Gfx.argb(0xFFFFFF, 0.09f * a));
        float ratio = MathHelper.clamp(shown / max, 0f, 1f);
        float tr2 = MathHelper.clamp(trail / max, 0f, 1f);
        int tw = (int) (bw * tr2);
        if (tw > (int) (bw * ratio) + 1) Gfx.rrect(ctx, bx, by, Math.max(bh, tw), bh, 3, Gfx.argb(0xFFFFFF, 0.30f * a));
        int fw = (int) (bw * ratio);
        if (fw > 0) {
            int w = Math.max(bh, fw);
            int ce = Gfx.lerpArgb(Gfx.argb(c1, a), Gfx.argb(c2, a), ratio);
            Gfx.rboxH(ctx, bx, by, w, bh, 3, Gfx.argb(c1, a), ce);
            ctx.fill(bx + 3, by + 1, bx + w - 3, by + 2, Gfx.argb(0xFFFFFF, 0.30f * a));
            Gfx.circle(ctx, bx + w - 2, by + bh / 2, 2, Gfx.argb(0xFFFFFF, 0.55f * a));
        }

        // broń i zbroja (tylko założone przedmioty, bez pustych kratek)
        if (cfg.targetArmor) {
            int iy = y + 40;
            if (items.isEmpty()) {
                Gfx.text(ctx, tr, "Brak zbroi", rx, iy + 4, Gfx.argb(0x6B7488, a), false);
            } else {
                int ix = rx;
                for (ItemStack st : items) {
                    if (ix + 16 > rr + 4) break;
                    Gfx.rrect(ctx, ix - 1, iy - 1, 18, 18, 4, Gfx.argb(0xFFFFFF, 0.06f * a));
                    if (a > 0.6f) {
                        ctx.drawItem(st, ix, iy);
                        ctx.drawStackOverlay(tr, st, ix, iy);
                    }
                    ix += 20;
                }
            }
        }
    }
}
