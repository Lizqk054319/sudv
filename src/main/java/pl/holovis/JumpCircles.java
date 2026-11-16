package pl.holovis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Jump Circles: przy skoku pod nogami pojawia się rozszerzający się wzór (koło, sześciokąt, gwiazda...). */
public final class JumpCircles {
    private JumpCircles() {}

    public static final String[] STYLES = {"Koło", "Sześciokąt", "Gwiazda", "Kwadrat", "Trójkąt", "Podwójne koło", "Fala", "Promienie"};

    private record Circle(double x, double y, double z, long start, float spin) {}
    private static final List<Circle> LIST = new ArrayList<>();
    private static final Map<Integer, Boolean> GROUND = new HashMap<>();
    private static final Map<Integer, Double> LAST_Y = new HashMap<>();

    public static void tick(MinecraftClient mc) {
        HoloConfig c = HoloConfig.get();
        if (!c.jumpCircles || mc.world == null || mc.player == null) { GROUND.clear(); LAST_Y.clear(); return; }
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && !c.jumpOthers) continue;
            if (p.isSpectator() || p.isInvisible()) continue;
            boolean on = p.isOnGround();
            Boolean was = GROUND.put(p.getId(), on);
            Double ly = LAST_Y.put(p.getId(), p.getY());
            if (was != null && was && !on && ly != null && p.getY() > ly + 0.01 && !p.isClimbing() && !p.isTouchingWater()) {
                if (LIST.size() < 64)
                    LIST.add(new Circle(p.getX(), ly + 0.02, p.getZ(), Util.getMeasuringTimeMs(), (float) (Math.random() * Math.PI)));
            }
        }
        if (GROUND.size() > 256) { GROUND.clear(); LAST_Y.clear(); }
    }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }

    /** Obrys wielokąta (albo gwiazdy, gdy inner > 0) leżący na ziemi. */
    private static void poly(VertexConsumer vc, Matrix4f m, int n, float r, float w, float rot, float starInner,
                             int c1, int c2, float a) {
        int pts = starInner > 0 ? n * 2 : n;
        float[] ox = new float[pts + 1], oz = new float[pts + 1], ix = new float[pts + 1], iz = new float[pts + 1];
        for (int i = 0; i <= pts; i++) {
            double ang = rot + i * Math.PI * 2 / pts;
            float rr = (starInner > 0 && i % 2 == 1) ? r * starInner : r;
            float ri = Math.max(0f, rr - w);
            ox[i] = (float) Math.cos(ang) * rr; oz[i] = (float) Math.sin(ang) * rr;
            ix[i] = (float) Math.cos(ang) * ri; iz[i] = (float) Math.sin(ang) * ri;
        }
        for (int i = 0; i < pts; i++) {
            int col = WorldFx.argb(Gfx.mix(Gfx.mix(c1, c2, (float) Math.abs(Math.sin(i * Math.PI / pts))), 0xFFFFFF, a > 0.6f ? 0.18f : 0f), a);
            WorldFx.quad2(vc, m, col, ox[i], 0f, oz[i], ox[i + 1], 0f, oz[i + 1], ix[i + 1], 0f, iz[i + 1], ix[i], 0f, iz[i]);
        }
    }

    public static void render(WorldRenderContext ctx) {
        HoloConfig c = HoloConfig.get();
        if (!c.jumpCircles || LIST.isEmpty()) { if (!c.jumpCircles) LIST.clear(); return; }
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        Vec3d cam = ctx.camera().getPos();
        long now = Util.getMeasuringTimeMs();
        float secs = Math.max(0.3f, c.jumpSecs);
        float size = Math.max(0.3f, c.jumpSize);
        int c1 = c.jumpColor.rgb(), c2 = c.jumpGradient ? c.jumpColor2.rgb() : c1;
        VertexConsumer vc = vcp.getBuffer(WorldFx.flat());

        Iterator<Circle> it = LIST.iterator();
        while (it.hasNext()) {
            Circle ci = it.next();
            float t = (now - ci.start()) / (secs * 1000f);
            if (t >= 1f) { it.remove(); continue; }
            float e = easeOut(t);
            float a = Math.min(1f, (float) Math.pow(1f - t, 1.05) * 1.25f);
            float r = size * (0.32f + 0.95f * e);
            float rot = ci.spin() + t * 1.6f;
            ms.push();
            ms.translate((float) (ci.x() - cam.x), (float) (ci.y() - cam.y) + 0.015f, (float) (ci.z() - cam.z));
            Matrix4f m = ms.peek().getPositionMatrix();
            float w = 0.12f * size;
            switch (c.jumpStyle) {
                case 1 -> { poly(vc, m, 6, r + 0.08f, w * 2.6f, rot, 0, c1, c2, a * 0.38f); poly(vc, m, 6, r, w, rot, 0, c1, c2, a); }
                case 2 -> { poly(vc, m, 5, r + 0.08f, w * 2.6f, rot, 0.45f, c1, c2, a * 0.38f); poly(vc, m, 5, r, w, rot, 0.45f, c1, c2, a); }
                case 3 -> { poly(vc, m, 4, r + 0.08f, w * 2.6f, rot, 0, c1, c2, a * 0.38f); poly(vc, m, 4, r, w, rot, 0, c1, c2, a); }
                case 4 -> { poly(vc, m, 3, r + 0.08f, w * 2.6f, rot, 0, c1, c2, a * 0.38f); poly(vc, m, 3, r, w, rot, 0, c1, c2, a); }
                case 5 -> {
                    poly(vc, m, 40, r + 0.08f, w * 2.6f, rot, 0, c1, c2, a * 0.35f);
                    poly(vc, m, 40, r, w, rot, 0, c1, c2, a);
                    float r2 = size * (0.15f + 0.6f * easeOut(Math.max(0f, t - 0.12f) / 0.88f));
                    poly(vc, m, 40, r2, w * 0.8f, -rot, 0, c2, c1, a * 0.8f);
                }
                case 6 -> {
                    for (int k = 0; k < 3; k++) {
                        float tk = t - k * 0.16f;
                        if (tk <= 0f) continue;
                        float ek = easeOut(Math.min(1f, tk / (1f - k * 0.16f)));
                        float rk = size * (0.25f + 1.0f * ek);
                        poly(vc, m, 40, rk + 0.06f, w * 2f, rot, 0, c1, c2, a * 0.3f * (1f - k * 0.25f));
                        poly(vc, m, 40, rk, w * 0.9f, rot, 0, c1, c2, a * (1f - k * 0.2f));
                    }
                }
                case 7 -> {
                    poly(vc, m, 40, r + 0.08f, w * 2.6f, rot, 0, c1, c2, a * 0.35f);
                    poly(vc, m, 40, r, w, rot, 0, c1, c2, a);
                    for (int k = 0; k < 12; k++) {
                        double ang = rot + k * Math.PI / 6;
                        float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang), px = -sn * 0.035f * size, pz = cs * 0.035f * size;
                        float r0 = r * 0.55f, r1 = r * 0.92f;
                        WorldFx.quad2(vc, m, WorldFx.argb(k % 2 == 0 ? c1 : c2, a * 0.85f),
                                cs * r0 + px, 0f, sn * r0 + pz, cs * r1 + px, 0f, sn * r1 + pz, cs * r1 - px, 0f, sn * r1 - pz, cs * r0 - px, 0f, sn * r0 - pz);
                    }
                }
                default -> {
                    WorldFx.flatRing(vc, m, 0f, r, 0f, WorldFx.argb(c1, 0.18f * a), WorldFx.argb(c2, 0.35f * a), 40);
                    poly(vc, m, 40, r + 0.08f, w * 2.6f, rot, 0, c1, c2, a * 0.38f);
                    poly(vc, m, 40, r, w, rot, 0, c1, c2, a);
                }
            }
            ms.pop();
        }
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(WorldFx.flat());
    }
}
