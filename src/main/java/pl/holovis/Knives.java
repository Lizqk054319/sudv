package pl.holovis;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Knife: zamiast miecza w pierwszej osobie rysujemy nóż z CS2 (Karambit, Bayonet, Butterfly)
 * w wybranym wykończeniu (Stal, Sapphire, Ruby, Emerald, Black Pearl). Geometria jest budowana w kodzie,
 * więc nie potrzeba żadnych dodatkowych plików modelu.
 */
public final class Knives {
    private Knives() {}

    public static final String[] TYPES = {"Karambit", "Bayonet", "Butterfly"};
    public static final String[] FINISHES = {"Stal", "Sapphire", "Ruby", "Emerald", "Black Pearl"};
    public static final String[] ITEMS = {"Wszystkie miecze", "Diamentowy miecz", "Netherytowy miecz", "Żelazny miecz",
            "Złoty miecz", "Kamienny miecz", "Drewniany miecz"};
    private static final String[] ITEM_IDS = {"", "diamond_sword", "netherite_sword", "iron_sword", "golden_sword",
            "stone_sword", "wooden_sword"};

    public static boolean isSword(ItemStack st) {
        if (st == null || st.isEmpty()) return false;
        return Registries.ITEM.getId(st.getItem()).getPath().endsWith("_sword");
    }

    public static boolean replaces(ItemStack st) {
        HoloConfig c = HoloConfig.get();
        if (!c.knifeOn || st == null || st.isEmpty()) return false;
        String path = Registries.ITEM.getId(st.getItem()).getPath();
        int i = c.knifeItem;
        if (i <= 0 || i >= ITEM_IDS.length) return path.endsWith("_sword");
        return path.equals(ITEM_IDS[i]);
    }

    // ------------------------------------------------------------------ szum (marmurkowanie)
    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535f;
    }

    private static float noise(float x, float y) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = hash(xi, yi), b = hash(xi + 1, yi), c = hash(xi, yi + 1), d = hash(xi + 1, yi + 1);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    private static float fbm(float x, float y) {
        return noise(x, y) * 0.6f + noise(x * 2.1f + 7.3f, y * 2.1f - 3.1f) * 0.28f + noise(x * 4.3f - 1.7f, y * 4.3f + 9.4f) * 0.12f;
    }

    private static int mix3(int a, int b, int c, float t) {
        return t < 0.5f ? Gfx.mix(a, b, t * 2f) : Gfx.mix(b, c, (t - 0.5f) * 2f);
    }

    // paleta wykończeń: {ciemny, główny, jasny, akcent}
    private static final int[][] PAL = {
            {0x6A7380, 0xB8C0CC, 0xEEF2F8, 0x9AA4B4},   // stal
            {0x05061E, 0x1B34E6, 0x7C58FF, 0x3A8CFF},   // sapphire
            {0x2A0006, 0xC90F22, 0xFF6A5C, 0x8A0A1A},   // ruby
            {0x021E12, 0x0B9C47, 0x86FFB0, 0x0A6A3A},   // emerald
            {0x050507, 0x23232C, 0x6C6C82, 0x14141A}};  // black pearl

    /** Kolor powierzchni ostrza w punkcie (u = wzdłuż, v = w poprzek, 0..1). */
    private static int paint(int finish, float u, float v, float side) {
        int[] p = PAL[Math.max(0, Math.min(PAL.length - 1, finish))];
        if (finish == 0) {
            float streak = noise(u * 60f + side * 11f, v * 2.5f);
            float t = 0.25f + 0.5f * (1f - v) * 0.6f + streak * 0.45f;
            return mix3(p[0], p[1], p[2], Math.max(0f, Math.min(1f, t)));
        }
        float wx = noise(u * 2.2f + side * 5f, v * 3f) * 1.8f;
        float t = fbm(u * 3.4f + wx + v * 1.6f, v * 5.5f - wx * 0.7f + side * 3f);
        t = Math.max(0f, Math.min(1f, (t - 0.22f) * 2.5f));
        int c = mix3(p[0], p[1], p[2], t);
        // żyłki akcentowe
        float vein = Math.abs(noise(u * 7f + 3f, v * 9f + side) - 0.5f);
        if (vein < 0.045f) c = Gfx.mix(c, p[3], 0.65f);
        return c;
    }

    // ------------------------------------------------------------------ pędzel
    private static final class Brush {
        final VertexConsumer vc;
        final Matrix4f m;
        final float time;
        // światło w układzie noża
        static final float LX = -0.35f, LY = 0.45f, LZ = 0.82f;

        Brush(VertexConsumer vc, Matrix4f m) {
            this.vc = vc;
            this.m = m;
            this.time = (Util.getMeasuringTimeMs() % 100000L) / 1000f;
        }

        /** Czworokąt z kolorem; normalna zorientowana od punktu (ox,oy,oz) na zewnątrz. */
        void quad(int rgb, float sheenU, boolean metal, float ox, float oy, float oz,
                  float x1, float y1, float z1, float x2, float y2, float z2,
                  float x3, float y3, float z3, float x4, float y4, float z4) {
            float ux = x2 - x1, uy = y2 - y1, uz = z2 - z1;
            float vx = x4 - x1, vy = y4 - y1, vz = z4 - z1;
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1e-9f) return;
            nx /= nl; ny /= nl; nz /= nl;
            float cx = (x1 + x2 + x3 + x4) * 0.25f - ox, cy = (y1 + y2 + y3 + y4) * 0.25f - oy, cz = (z1 + z2 + z3 + z4) * 0.25f - oz;
            if (nx * cx + ny * cy + nz * cz < 0f) { nx = -nx; ny = -ny; nz = -nz; }
            float d = Math.max(0f, nx * LX + ny * LY + nz * LZ);
            float k = 0.52f + 0.48f * d;
            if (metal) {
                float spec = (float) Math.pow(d, 14.0) * 0.35f;
                float band = Math.max(0f, 1f - Math.abs(((sheenU * 1.4f - time * 0.55f) % 1f + 1f) % 1f - 0.5f) * 7f);
                k += spec + band * 0.20f;
            }
            int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
            int rr = Math.min(255, (int) (r * k)), gg = Math.min(255, (int) (g * k)), bb = Math.min(255, (int) (b * k));
            if (k > 1f) {   // jasne refleksy wybielają
                float w = Math.min(0.5f, k - 1f);
                rr = (int) (rr + (255 - rr) * w); gg = (int) (gg + (255 - gg) * w); bb = (int) (bb + (255 - bb) * w);
            }
            int c = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
            WorldFx.quad2(vc, m, c, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4);
        }

        /** Prostopadłościan obrócony o rotZ (rad) wokół osi Z; środek (cx,cy,cz), połowy rozmiarów (hx,hy,hz). */
        void box(int rgb, boolean metal, float cx, float cy, float cz, float hx, float hy, float hz, float rotZ) {
            float cs = (float) Math.cos(rotZ), sn = (float) Math.sin(rotZ);
            float[][] c = new float[8][3];
            for (int i = 0; i < 8; i++) {
                float lx = (i & 1) == 0 ? -hx : hx, ly = (i & 2) == 0 ? -hy : hy, lz = (i & 4) == 0 ? -hz : hz;
                c[i][0] = cx + lx * cs - ly * sn;
                c[i][1] = cy + lx * sn + ly * cs;
                c[i][2] = cz + lz;
            }
            int[][] f = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
            for (int[] q : f) {
                quad(rgb, cy, metal, cx, cy, cz,
                        c[q[0]][0], c[q[0]][1], c[q[0]][2], c[q[1]][0], c[q[1]][1], c[q[1]][2],
                        c[q[2]][0], c[q[2]][1], c[q[2]][2], c[q[3]][0], c[q[3]][1], c[q[3]][2]);
            }
        }

        /**
         * Ostrze zbudowane z przekrojów: grzbiet S[i] i krawędź tnąca E[i] (punkty 2D w płaszczyźnie XY),
         * grubość grzbietu t. Powierzchnie dostają wzór wykończenia.
         */
        void blade(int finish, float[][] S, float[][] E, float t) {
            final float[] fr = {0f, 0.18f, 0.38f, 0.56f, 0.72f, 0.87f, 1f};
            final float[] zf = {1f, 0.96f, 0.9f, 0.84f, 0.6f, 0.3f, 0f};
            int n = S.length - 1;
            int[] pal = PAL[Math.max(0, Math.min(PAL.length - 1, finish))];
            for (int i = 0; i < n; i++) {
                float u0 = i / (float) n, u1 = (i + 1) / (float) n, um = (u0 + u1) * 0.5f;
                float ox = (S[i][0] + E[i][0]) * 0.5f, oy = (S[i][1] + E[i][1]) * 0.5f;
                for (int side = -1; side <= 1; side += 2) {
                    for (int k = 0; k < fr.length - 1; k++) {
                        float a0 = fr[k], a1 = fr[k + 1];
                        float xa0 = S[i][0] + (E[i][0] - S[i][0]) * a0, ya0 = S[i][1] + (E[i][1] - S[i][1]) * a0;
                        float xa1 = S[i][0] + (E[i][0] - S[i][0]) * a1, ya1 = S[i][1] + (E[i][1] - S[i][1]) * a1;
                        float xb0 = S[i + 1][0] + (E[i + 1][0] - S[i + 1][0]) * a0, yb0 = S[i + 1][1] + (E[i + 1][1] - S[i + 1][1]) * a0;
                        float xb1 = S[i + 1][0] + (E[i + 1][0] - S[i + 1][0]) * a1, yb1 = S[i + 1][1] + (E[i + 1][1] - S[i + 1][1]) * a1;
                        int col = paint(finish, um, (a0 + a1) * 0.5f, side);
                        quad(col, u0, true, ox, oy, 0f,
                                xa0, ya0, t * zf[k] * side, xb0, yb0, t * zf[k] * side,
                                xb1, yb1, t * zf[k + 1] * side, xa1, ya1, t * zf[k + 1] * side);
                    }
                }
                // grzbiet
                int dark = Gfx.mix(pal[0], 0x303640, 0.4f);
                quad(dark, u0, false, E[i][0], E[i][1], 0f,
                        S[i][0], S[i][1], t, S[i + 1][0], S[i + 1][1], t, S[i + 1][0], S[i + 1][1], -t, S[i][0], S[i][1], -t);
            }
        }
    }

    // ------------------------------------------------------------------ modele
    private static float[][][] curve(int n, java.util.function.IntFunction<float[][]> f) {
        float[][] a = new float[n + 1][], b = new float[n + 1][];
        for (int i = 0; i <= n; i++) { float[][] p = f.apply(i); a[i] = p[0]; b[i] = p[1]; }
        return new float[][][]{a, b};
    }

    private static void karambit(Brush b, int finish) {
        // uchwyt (ciemny) + pierścień na palec
        int grip = 0x1C1D24, gripHi = 0x2C2E3A;
        b.box(grip, false, 0f, 0.20f, 0f, 0.052f, 0.17f, 0.030f, 0f);
        for (int i = 0; i < 5; i++) b.box(gripHi, false, 0f, 0.07f + i * 0.065f, 0f, 0.056f, 0.012f, 0.033f, 0f);
        int N = 18;
        for (int i = 0; i < N; i++) {
            double a1 = i * Math.PI * 2 / N, a2 = (i + 1) * Math.PI * 2 / N;
            double am = (a1 + a2) / 2;
            float rr = 0.075f;
            float cx = (float) Math.cos(am) * rr, cy = -0.035f + (float) Math.sin(am) * rr;
            b.box(0xA8B0BE, true, cx, cy, 0f, 0.020f, 0.010f, 0.016f, (float) (am + Math.PI / 2));
        }
        // zakrzywione ostrze: łuk w stronę +X, krawędź tnąca po wewnętrznej (wklęsłej) stronie
        final float R = 0.40f, y0 = 0.34f, phiMax = 1.62f;
        float[][][] sb = curve(26, i -> {
            float u = i / 26f, phi = phiMax * u;
            float px = R - R * (float) Math.cos(phi), py = y0 + R * (float) Math.sin(phi);
            float ix = (float) Math.cos(phi), iy = -(float) Math.sin(phi);   // do środka łuku
            float w = 0.078f * (float) Math.pow(1f - u, 0.75) * Math.min(1f, 0.45f + u * 5f);
            return new float[][]{{px - ix * w, py - iy * w}, {px + ix * w * 1.15f, py + iy * w * 1.15f}};
        });
        b.blade(finish, sb[0], sb[1], 0.017f);
    }

    private static void bayonet(Brush b, int finish) {
        int grip = 0xC4BC86, gripDark = 0x8F8A5E;
        b.box(grip, false, 0f, 0.16f, 0f, 0.046f, 0.15f, 0.028f, 0f);
        for (int i = 0; i < 7; i++) b.box(gripDark, false, 0f, 0.035f + i * 0.04f, 0f, 0.049f, 0.008f, 0.031f, 0f);
        b.box(0xA8B0BE, true, 0f, -0.012f, 0f, 0.055f, 0.016f, 0.034f, 0f);          // głowica
        b.box(0x8C94A2, true, 0f, 0.325f, 0f, 0.125f, 0.018f, 0.034f, 0f);           // jelec
        b.box(0x8C94A2, true, 0.115f, 0.345f, 0f, 0.012f, 0.03f, 0.034f, 0f);
        // ostrze: prosty grzbiet z wykrojem (clip point), brzuszek przy czubku
        float[][][] sb = curve(24, i -> {
            float u = i / 24f;
            float y = 0.34f + 0.66f * u;
            float sx = -0.056f + (u > 0.70f ? 0.062f * (float) Math.pow((u - 0.70f) / 0.30f, 1.6) : 0f);
            float ex = 0.056f - (u > 0.78f ? 0.056f * (float) Math.pow((u - 0.78f) / 0.22f, 1.4) : 0f);
            if (u > 0.985f) { sx = 0.005f; ex = 0.005f; }
            return new float[][]{{sx, y}, {ex, y}};
        });
        b.blade(finish, sb[0], sb[1], 0.014f);
    }

    private static void butterfly(Brush b, int finish) {
        int h = 0x3C424E, hHi = 0x59606E;
        for (int side = -1; side <= 1; side += 2) {
            float z = 0.034f * side;
            b.box(h, true, 0.0f, 0.19f, z, 0.050f, 0.20f, 0.010f, 0f);
            b.box(hHi, true, 0.0f, 0.19f, z + 0.004f * side, 0.022f, 0.17f, 0.007f, 0f);
            for (int i = 0; i < 3; i++) b.box(0x15171C, false, 0f, 0.10f + i * 0.09f, z + 0.010f * side, 0.014f, 0.028f, 0.003f, 0f);
        }
        b.box(0xA8B0BE, true, 0f, 0.06f, 0f, 0.044f, 0.012f, 0.040f, 0f);   // sworznie
        b.box(0xA8B0BE, true, 0f, 0.34f, 0f, 0.044f, 0.012f, 0.040f, 0f);
        float[][][] sb = curve(24, i -> {
            float u = i / 24f;
            float y = 0.30f + 0.72f * u;
            float sx = -0.052f + (u > 0.78f ? 0.056f * (float) Math.pow((u - 0.78f) / 0.22f, 1.5) : 0f);
            float ex = 0.052f - (u > 0.86f ? 0.050f * (float) Math.pow((u - 0.86f) / 0.14f, 1.3) : 0f);
            if (u > 0.985f) { sx = 0.003f; ex = 0.003f; }
            return new float[][]{{sx, y}, {ex, y}};
        });
        b.blade(finish, sb[0], sb[1], 0.011f);
    }

    // ------------------------------------------------------------------ wejście
    /**
     * Rysuje nóż zamiast przedmiotu w ręce (pierwsza osoba). Macierz ma już pozycję i ruch ręki;
     * dokładamy transformację "trzymanego przedmiotu" i narysowany nóż leży tam, gdzie leżałby miecz.
     */
    /** Pozycja ręki jak w grze (machnięcie + wyciąganie), potem nóż. */
    public static void drawHand(MatrixStack ms, VertexConsumerProvider vcp, boolean right, float swing, float equip) {
        int l = right ? 1 : -1;
        ms.push();
        float sq = (float) Math.sqrt(swing);
        float n = -0.4f * (float) Math.sin(sq * Math.PI);
        float m = 0.2f * (float) Math.sin(sq * Math.PI * 2);
        float f = -0.2f * (float) Math.sin(swing * Math.PI);
        ms.translate(l * n, m, f);
        ms.translate(l * 0.56f, -0.52f + equip * -0.6f, -0.72f);
        float g = (float) Math.sin(swing * swing * Math.PI);
        ms.multiply(new Quaternionf().rotateY((float) Math.toRadians(l * (45f + g * -20f))));
        float h = (float) Math.sin(sq * Math.PI);
        ms.multiply(new Quaternionf().rotateZ((float) Math.toRadians(l * h * -20f)));
        ms.multiply(new Quaternionf().rotateX((float) Math.toRadians(h * -80f)));
        ms.multiply(new Quaternionf().rotateY((float) Math.toRadians(l * -45f)));
        drawFirstPerson(ms, vcp, !right);
        ms.pop();
    }

    public static void drawFirstPerson(MatrixStack ms, VertexConsumerProvider vcp, boolean leftHanded) {
        HoloConfig c = HoloConfig.get();
        ms.push();
        float sg = leftHanded ? -1f : 1f;
        ms.translate(sg * 1.13f / 16f, 3.2f / 16f, 1.13f / 16f);
        ms.multiply(new Quaternionf().rotationXYZ(0f, (float) Math.toRadians(-90f * sg), (float) Math.toRadians(25f * sg)));
        ms.scale(0.68f, 0.68f, 0.68f);
        ms.translate(-0.5f, -0.5f, -0.5f);
        // lokalna oś Y noża -> przekątna sprite'a (jak ostrze miecza)
        ms.translate(0.17f, 0.17f, 0.5f);
        ms.multiply(new Quaternionf().rotateZ((float) Math.toRadians(-45f)));
        float s = 0.92f * Math.max(0.3f, c.knifeSize);
        ms.scale(s, s, s);

        // zwykła warstwa przedmiotów (jak miecz), bez mgły "beacon beam" - nóż nie znika np. po walce w nocy
        RenderLayer layer = RenderLayer.getEntityCutoutNoCull(WorldFx.WHITE);
        VertexConsumer vc = vcp.getBuffer(layer);
        Brush b = new Brush(vc, ms.peek().getPositionMatrix());
        switch (c.knifeType) {
            case 1 -> bayonet(b, c.knifeFinish);
            case 2 -> butterfly(b, c.knifeFinish);
            default -> karambit(b, c.knifeFinish);
        }
        ms.pop();
    }
}
