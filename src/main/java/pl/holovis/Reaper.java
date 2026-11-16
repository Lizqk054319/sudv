package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Kosiarz do Kill Effectu: postać w czarnej szacie z kapturem, świecącymi oczami i kosą.
 * Rysowana w układzie lokalnym: +X = w stronę zabitego gracza, Y = góra.
 */
public final class Reaper {
    private Reaper() {}

    private record Snd(long at, String id, float pitch, float vol) {}
    private static final List<Snd> SOUNDS = new ArrayList<>();

    public static void sounds(long start) {
        SOUNDS.add(new Snd(start + 80, "entity.wither.ambient", 1.6f, 0.35f));
        SOUNDS.add(new Snd(start + 600, "entity.player.attack.sweep", 0.8f, 1f));
        SOUNDS.add(new Snd(start + 720, "entity.player.attack.strong", 0.9f, 1f));
        SOUNDS.add(new Snd(start + 730, "entity.player.attack.knockback", 1f, 1f));
    }

    public static void tickSounds(long ignored) {
        if (SOUNDS.isEmpty()) return;
        long now = Util.getMeasuringTimeMs();
        Iterator<Snd> it = SOUNDS.iterator();
        while (it.hasNext()) {
            Snd s = it.next();
            if (now < s.at()) continue;
            it.remove();
            if (now - s.at() > 500) continue;
            SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of("minecraft", s.id()));
            if (ev != null) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(ev, s.pitch(), s.vol()));
        }
    }

    // ------------------------------------------------------------------ geometria
    private static int shade(int rgb, float k) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * k)), g = Math.min(255, (int) (((rgb >> 8) & 255) * k)),
                b = Math.min(255, (int) ((rgb & 255) * k));
        return (r << 16) | (g << 8) | b;
    }

    /** Ścięty stożek (szata, kaptur) wokół osi Y przesuniętej o (ox, oz). */
    private static void frustum(VertexConsumer vc, Matrix4f m, float ox, float oz, float y0, float r0, float y1, float r1,
                                int rgb, float a, int seg, float ragged) {
        for (int i = 0; i < seg; i++) {
            double a1 = i * Math.PI * 2 / seg, a2 = (i + 1) * Math.PI * 2 / seg;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            float k = 0.55f + 0.45f * Math.max(0f, (float) (Math.cos((a1 + a2) / 2 - 0.7)));
            float h1 = ragged * ((i * 37) % 5) / 5f, h2 = ragged * (((i + 1) * 37) % 5) / 5f;
            WorldFx.quad2(vc, m, WorldFx.argb(shade(rgb, k), a),
                    ox + c1 * r0, y0 + h1, oz + s1 * r0, ox + c2 * r0, y0 + h2, oz + s2 * r0,
                    ox + c2 * r1, y1, oz + s2 * r1, ox + c1 * r1, y1, oz + s1 * r1);
        }
    }

    /** Pręt między dwoma punktami w płaszczyźnie XY (dwie skrzyżowane wstęgi). */
    private static void rod(VertexConsumer vc, Matrix4f m, float x1, float y1, float x2, float y2, float w, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-5f) return;
        float nx = -dy / l * w, ny = dx / l * w;
        WorldFx.quad2(vc, m, argb, x1 + nx, y1 + ny, 0f, x2 + nx, y2 + ny, 0f, x2 - nx, y2 - ny, 0f, x1 - nx, y1 - ny, 0f);
        WorldFx.quad2(vc, m, argb, x1, y1, w, x2, y2, w, x2, y2, -w, x1, y1, -w);
    }

    private static float ease(float t) { t = Math.max(0f, Math.min(1f, t)); return t * t * (3 - 2 * t); }

    /** Kąt kosy (stopnie; 0 = pionowo, dodatni = pochylona do przodu). */
    private static float angle(float el) {
        if (el < 0.30f) return -15f;
        if (el < 0.60f) return -15f - 60f * ease((el - 0.30f) / 0.30f);
        if (el < 0.76f) return -75f + 195f * ease((el - 0.60f) / 0.16f);
        if (el < 2.2f) return 120f - 70f * ease((el - 0.76f) / 1.2f);
        return 50f;
    }

    public static void draw(MatrixStack ms, VertexConsumerProvider vcp, float el, float alpha, int glow) {
        Matrix4f m = ms.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(WorldFx.flat());
        int robe = 0x17131F;

        // poświata pod stopami
        WorldFx.flatRing(vc, m, 0f, 0.75f, 0.03f, WorldFx.argb(glow, 0.35f * alpha), WorldFx.argb(glow, 0f), 24);
        // szata, ramiona, kaptur
        frustum(vc, m, 0f, 0f, 0.0f, 0.46f, 1.45f, 0.22f, robe, alpha, 12, 0.10f);
        frustum(vc, m, 0f, 0f, 1.40f, 0.23f, 1.60f, 0.27f, robe, alpha, 12, 0f);
        frustum(vc, m, -0.04f, 0f, 1.58f, 0.27f, 2.08f, 0.04f, shade(robe, 1.15f), alpha, 12, 0f);
        // ciemna twarz i świecące oczy (z przodu, +X)
        WorldFx.quad2(vc, m, WorldFx.argb(0x000000, alpha), 0.235f, 1.62f, -0.13f, 0.235f, 1.62f, 0.13f, 0.20f, 1.90f, 0.10f, 0.20f, 1.90f, -0.10f);
        for (int s = -1; s <= 1; s += 2) {
            float z = s * 0.065f;
            WorldFx.quad2(vc, m, WorldFx.argb(glow, 0.35f * alpha), 0.25f, 1.70f, z - 0.05f, 0.25f, 1.70f, z + 0.05f, 0.25f, 1.80f, z + 0.05f, 0.25f, 1.80f, z - 0.05f);
            WorldFx.quad2(vc, m, WorldFx.argb(Gfx.mix(glow, 0xFFFFFF, 0.5f), alpha), 0.255f, 1.73f, z - 0.022f, 0.255f, 1.73f, z + 0.022f, 0.255f, 1.77f, z + 0.022f, 0.255f, 1.77f, z - 0.022f);
        }

        // kosa: obrót wokół dłoni
        float ang = (float) Math.toRadians(angle(el));
        float px = 0.36f, py = 1.12f;
        float dx = (float) Math.sin(ang), dy = (float) Math.cos(ang);
        // ręce do trzonka
        for (int s = -1; s <= 1; s += 2) {
            ms.push();
            ms.translate(0f, 0f, s * 0.13f);
            Matrix4f ma = ms.peek().getPositionMatrix();
            rod(vc, ma, 0.05f, 1.42f, px + dx * 0.1f * s, py + dy * 0.1f * s, 0.06f, WorldFx.argb(robe, alpha));
            ms.pop();
        }
        float bx = px - dx * 0.65f, by = py - dy * 0.65f, tx = px + dx * 1.45f, ty = py + dy * 1.45f;
        rod(vc, m, bx, by, tx, ty, 0.035f, WorldFx.argb(0x3A2A1E, alpha));
        // ostrze: łuk od czubka trzonka do przodu i w dół
        float nx = dy, ny = -dx;   // prostopadle do trzonka, w stronę ofiary
        int segs = 10;
        float[] ex = new float[segs + 1], ey = new float[segs + 1], ix = new float[segs + 1], iy = new float[segs + 1];
        for (int i = 0; i <= segs; i++) {
            float s = i / (float) segs;
            float cx = tx + nx * 0.95f * s - dx * 0.38f * s * s;
            float cy = ty + ny * 0.95f * s - dy * 0.38f * s * s;
            float w = 0.15f * (1f - s) + 0.008f;
            ex[i] = cx; ey[i] = cy;
            ix[i] = cx - dx * w; iy[i] = cy - dy * w;
        }
        for (int i = 0; i < segs; i++) {
            float k = 0.75f + 0.25f * (float) Math.sin(i * 0.9 + el * 6.0);
            WorldFx.quad2(vc, m, WorldFx.argb(shade(0xC9CEDB, k), alpha),
                    ex[i], ey[i], 0.012f, ex[i + 1], ey[i + 1], 0.012f, ix[i + 1], iy[i + 1], 0.012f, ix[i], iy[i], 0.012f);
            WorldFx.quad2(vc, m, WorldFx.argb(shade(0xA9AEBB, k), alpha),
                    ex[i], ey[i], -0.012f, ex[i + 1], ey[i + 1], -0.012f, ix[i + 1], iy[i + 1], -0.012f, ix[i], iy[i], -0.012f);
            // świecąca krawędź
            WorldFx.quad2(vc, m, WorldFx.argb(glow, 0.55f * alpha),
                    ex[i], ey[i], 0.02f, ex[i + 1], ey[i + 1], 0.02f, ex[i + 1] + dx * 0.05f, ey[i + 1] + dy * 0.05f, 0.02f, ex[i] + dx * 0.05f, ey[i] + dy * 0.05f, 0.02f);
        }

        // smuga zamachu
        if (el > 0.58f && el < 1.05f) {
            float ta = el < 0.76f ? 1f : 1f - (el - 0.76f) / 0.29f;
            float a0 = (float) Math.toRadians(-75f), a1 = ang;
            int n = 16;
            for (int i = 0; i < n; i++) {
                float u0 = a0 + (a1 - a0) * i / n, u1 = a0 + (a1 - a0) * (i + 1) / n;
                float f0 = i / (float) n;
                float r0 = 1.45f, r1 = 2.30f;
                WorldFx.quad2(vc, m, WorldFx.argb(glow, 0.45f * ta * f0 * alpha),
                        px + (float) Math.sin(u0) * r0, py + (float) Math.cos(u0) * r0, 0f,
                        px + (float) Math.sin(u1) * r0, py + (float) Math.cos(u1) * r0, 0f,
                        px + (float) Math.sin(u1) * r1, py + (float) Math.cos(u1) * r1, 0f,
                        px + (float) Math.sin(u0) * r1, py + (float) Math.cos(u0) * r1, 0f);
            }
        }
    }
}
