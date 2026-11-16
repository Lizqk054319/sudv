package pl.holovis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * World Particles: wokół gracza (głównie przed nim) spadają gwiazdki, śnieg, płatki albo latają świetliki.
 * Po dotknięciu ziemi drobinka płynnie się rozpływa i znika (jak topniejący śnieg, tylko szybciej).
 */
public final class WorldParticles {
    private WorldParticles() {}

    public static final String[] TYPES = {"Gwiazdki", "Śnieg", "Płatki", "Świetliki"};

    private static final class P {
        double x, y, z;
        float vx, vy, vz, rot, spin, size, phase, age, life;
        float melt = -1f;   // >= 0: topnienie (sekundy)
        int type;
    }

    private static final List<P> LIST = new ArrayList<>();
    private static final Random R = new Random();
    private static long lastNs = System.nanoTime();
    private static float spawnAcc;

    private static int target(HoloConfig c) { return (int) (40 + 360 * Math.max(0f, Math.min(1f, c.wpAmount))); }

    private static void spawn(MinecraftClient mc, Camera cam, HoloConfig c) {
        Vec3d cp = cam.getPos();
        double yaw = Math.toRadians(cam.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double fwd = R.nextFloat() < 0.75f ? 2 + R.nextDouble() * 16 : -6 + R.nextDouble() * 8;
        double side = (R.nextDouble() * 2 - 1) * 14;
        P p = new P();
        p.type = Math.max(0, Math.min(TYPES.length - 1, c.wpType));
        p.x = cp.x + fx * fwd + rx * side;
        p.z = cp.z + fz * fwd + rz * side;
        p.y = p.type == 3 ? cp.y - 1 + R.nextDouble() * 5 : cp.y + 5 + R.nextDouble() * 9;
        float s = Math.max(0.3f, c.wpSize);
        switch (p.type) {
            case 1 -> { p.vy = -(0.9f + R.nextFloat() * 0.5f); p.size = (0.06f + R.nextFloat() * 0.05f) * s; }
            case 2 -> { p.vy = -(0.8f + R.nextFloat() * 0.4f); p.size = (0.08f + R.nextFloat() * 0.05f) * s; }
            case 3 -> { p.vy = 0f; p.size = (0.035f + R.nextFloat() * 0.025f) * s; p.life = 4f + R.nextFloat() * 3f; }
            default -> { p.vy = -(1.1f + R.nextFloat() * 0.6f); p.size = (0.06f + R.nextFloat() * 0.06f) * s; }
        }
        p.vx = (R.nextFloat() - 0.5f) * 0.3f;
        p.vz = (R.nextFloat() - 0.5f) * 0.3f;
        p.rot = R.nextFloat() * 6.28f;
        p.spin = (R.nextFloat() - 0.5f) * 3f;
        p.phase = R.nextFloat() * 6.28f;
        LIST.add(p);
    }

    public static void render(WorldRenderContext ctx) {
        HoloConfig c = HoloConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        long ns = System.nanoTime();
        float dt = Math.min(0.1f, (ns - lastNs) / 1_000_000_000f);
        lastNs = ns;
        if (!c.worldParticles || mc.world == null) { LIST.clear(); return; }
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        Camera cam = ctx.camera();
        Vec3d cp = cam.getPos();

        // dosypywanie nowych (płynnie, a nie wszystkie naraz)
        int tgt = target(c);
        spawnAcc += dt * tgt / 3.5f;
        while (spawnAcc >= 1f) {
            spawnAcc -= 1f;
            if (LIST.size() < tgt) spawn(mc, cam, c);
        }

        Quaternionf q = cam.getRotation();
        Vector3f right = q.transform(new Vector3f(1, 0, 0)), up = q.transform(new Vector3f(0, 1, 0));
        int c1 = c.wpColor.rgb();
        VertexConsumer vc = vcp.getBuffer(WorldFx.flat());
        Matrix4f m = ms.peek().getPositionMatrix();
        float t = (System.currentTimeMillis() % 100000L) / 1000f;

        Iterator<P> it = LIST.iterator();
        while (it.hasNext()) {
            P p = it.next();
            p.age += dt;
            if (p.melt >= 0f) {
                p.melt += dt;
                if (p.melt > 0.7f) { it.remove(); continue; }
            } else if (p.type == 3) {
                p.x += Math.sin(t * 0.9 + p.phase) * 0.35 * dt;
                p.z += Math.cos(t * 0.7 + p.phase * 1.3) * 0.35 * dt;
                p.y += Math.sin(t * 1.3 + p.phase) * 0.18 * dt;
                if (p.age > p.life) { it.remove(); continue; }
            } else {
                float sway = (float) Math.sin(t * 1.6 + p.phase) * (p.type == 2 ? 0.55f : 0.3f);
                p.x += (p.vx + sway) * dt;
                p.z += (p.vz + sway * 0.6f) * dt;
                p.y += p.vy * dt;
                p.rot += p.spin * dt;
                int gy = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(p.x), (int) Math.floor(p.z));
                if (p.y <= gy + 0.02) { p.y = gy + 0.02; p.melt = 0f; }
            }
            double dx = p.x - cp.x, dy = p.y - cp.y, dz = p.z - cp.z;
            if (dx * dx + dz * dz > 30 * 30 || dy < -24 || dy > 30) { it.remove(); continue; }

            float fadeIn = Math.min(1f, p.age / 0.5f);
            float a = fadeIn;
            if (p.type == 3) a *= Math.min(1f, (p.life - p.age) / 0.8f) * (0.55f + 0.45f * (float) Math.sin(t * 4 + p.phase));
            if (p.type == 0) a *= 0.65f + 0.35f * (float) Math.sin(t * 7 + p.phase);
            float cx = (float) dx, cy = (float) dy, cz = (float) dz;

            if (p.melt >= 0f) {
                // topnienie: kształt kładzie się na ziemi, rozlewa i znika
                float mt = p.melt / 0.7f;
                float s = p.size * (1f + 0.9f * mt);
                float ma = a * (1f - mt) * (1f - mt);
                shape(vc, m, p, cx, cy, cz, new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), s, c1, ma, t, 0.35f + 0.65f * (1f - mt));
            } else {
                shape(vc, m, p, cx, cy, cz, right, up, p.size, c1, a, t, 1f);
            }
        }
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(WorldFx.flat());
    }

    private static void q(VertexConsumer vc, Matrix4f m, int col, float cx, float cy, float cz, Vector3f r, Vector3f u,
                          float u1, float v1, float u2, float v2, float u3, float v3, float u4, float v4) {
        WorldFx.quad2(vc, m, col,
                cx + r.x * u1 + u.x * v1, cy + r.y * u1 + u.y * v1, cz + r.z * u1 + u.z * v1,
                cx + r.x * u2 + u.x * v2, cy + r.y * u2 + u.y * v2, cz + r.z * u2 + u.z * v2,
                cx + r.x * u3 + u.x * v3, cy + r.y * u3 + u.y * v3, cz + r.z * u3 + u.z * v3,
                cx + r.x * u4 + u.x * v4, cy + r.y * u4 + u.y * v4, cz + r.z * u4 + u.z * v4);
    }

    /** Rysuje kształt w płaszczyźnie (r, u); squash spłaszcza promienie przy topnieniu. */
    private static void shape(VertexConsumer vc, Matrix4f m, P p, float cx, float cy, float cz, Vector3f r, Vector3f u,
                              float s, int rgb, float a, float t, float squash) {
        if (a < 0.01f) return;
        float rot = p.rot;
        switch (p.type) {
            case 1 -> {   // płatek śniegu: 6 ramion
                int col = WorldFx.argb(Gfx.mix(rgb, 0xFFFFFF, 0.6f), a * 0.95f);
                for (int k = 0; k < 6; k++) {
                    double ang = rot + k * Math.PI / 3;
                    float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                    float w = s * 0.13f, L = s * squash;
                    q(vc, m, col, cx, cy, cz, r, u, -sn * w, cs * w, cs * L - sn * w, sn * L + cs * w, cs * L + sn * w, sn * L - cs * w, sn * w, -cs * w);
                }
                q(vc, m, WorldFx.argb(rgb, a * 0.25f), cx, cy, cz, r, u, -s * 0.8f, -s * 0.8f, s * 0.8f, -s * 0.8f, s * 0.8f, s * 0.8f, -s * 0.8f, s * 0.8f);
            }
            case 2 -> {   // płatek: wydłużony romb z odcieniem
                float cs = (float) Math.cos(rot), sn = (float) Math.sin(rot);
                float L = s * 1.4f, W = s * 0.55f * squash;
                q(vc, m, WorldFx.argb(Gfx.mix(rgb, 0xFFFFFF, 0.25f), a), cx, cy, cz, r, u,
                        cs * L, sn * L, -sn * W, cs * W, -cs * L, -sn * L, sn * W, -cs * W);
            }
            case 3 -> {   // świetlik: jasny rdzeń + miękka poświata
                float h = s * 3.5f;
                q(vc, m, WorldFx.argb(rgb, a * 0.18f), cx, cy, cz, r, u, -h, -h, h, -h, h, h, -h, h);
                q(vc, m, WorldFx.argb(rgb, a * 0.35f), cx, cy, cz, r, u, -h * 0.45f, -h * 0.45f, h * 0.45f, -h * 0.45f, h * 0.45f, h * 0.45f, -h * 0.45f, h * 0.45f);
                q(vc, m, WorldFx.argb(Gfx.mix(rgb, 0xFFFFFF, 0.6f), a), cx, cy, cz, r, u, -s, -s, s, -s, s, s, -s, s);
            }
            default -> {   // gwiazdka: 4 ramiona (latawce) + poświata
                int col = WorldFx.argb(Gfx.mix(rgb, 0xFFFFFF, 0.35f), a);
                float in = s * 0.28f;
                for (int k = 0; k < 4; k++) {
                    double ang = rot * 0.3 + k * Math.PI / 2;
                    float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                    float L = s * (k % 2 == 0 ? 1.3f : 1f) * squash;
                    float lx = (float) Math.cos(ang + Math.PI / 4) * in, ly = (float) Math.sin(ang + Math.PI / 4) * in;
                    float rx2 = (float) Math.cos(ang - Math.PI / 4) * in, ry2 = (float) Math.sin(ang - Math.PI / 4) * in;
                    q(vc, m, col, cx, cy, cz, r, u, 0f, 0f, rx2, ry2, cs * L, sn * L, lx, ly);
                }
                float h = s * 1.1f;
                q(vc, m, WorldFx.argb(rgb, a * 0.18f), cx, cy, cz, r, u, -h, -h, h, -h, h, h, -h, h);
            }
        }
    }
}
