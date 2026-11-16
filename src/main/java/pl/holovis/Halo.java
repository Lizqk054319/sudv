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

/** Halo: złota (albo dowolnego koloru) aureola nad głową gracza. */
public final class Halo {
    private Halo() {}

    private static void tube(VertexConsumer vc, Matrix4f m, float R, float r, int rgb, float alpha) {
        final int N = 32, S = 6;
        int hi = rgb, lo = Gfx.mix(rgb, 0x000000, 0.45f);   // bez rozjaśniania do bieli: czarne halo zostaje czarne
        for (int i = 0; i < N; i++) {
            double a1 = i * Math.PI * 2 / N, a2 = (i + 1) * Math.PI * 2 / N;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            for (int k = 0; k < S; k++) {
                double b1 = k * Math.PI * 2 / S, b2 = (k + 1) * Math.PI * 2 / S;
                float rr1 = R + (float) Math.cos(b1) * r, y1 = (float) Math.sin(b1) * r;
                float rr2 = R + (float) Math.cos(b2) * r, y2 = (float) Math.sin(b2) * r;
                float top = (float) (Math.sin((b1 + b2) / 2) * 0.5 + 0.5);   // góra rurki jaśniejsza
                int col = WorldFx.argb(Gfx.mix(lo, hi, top), alpha);
                WorldFx.quad2(vc, m, col,
                        c1 * rr1, y1, s1 * rr1, c2 * rr1, y1, s2 * rr1,
                        c2 * rr2, y2, s2 * rr2, c1 * rr2, y2, s1 * rr2);
            }
        }
    }

    public static void render(WorldRenderContext ctx) {
        HoloConfig cfg = HoloConfig.get();
        if (!cfg.halo) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;

        Vec3d cam = ctx.camera().getPos();
        float td = ctx.tickCounter().getTickDelta(false);
        boolean firstPerson = mc.options.getPerspective().isFirstPerson();
        long now = Util.getMeasuringTimeMs();
        int rgb = cfg.haloColor.rgb();
        float size = Math.max(0.5f, cfg.haloSize);

        VertexConsumer vc = null;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p.isRemoved() || p.isInvisible() || p.isSpectator()) continue;
            boolean self = p == mc.player;
            if (self && firstPerson) continue;
            if (!self && !cfg.haloOthers) continue;
            Vec3d lp = p.getLerpedPos(td);
            if (lp.squaredDistanceTo(cam) > 64 * 64) continue;

            if (vc == null) vc = vcp.getBuffer(WorldFx.flat());
            float bob = (float) Math.sin(now / 520.0 + p.getId()) * 0.018f;
            float y = p.getHeight() + cfg.haloHeight + bob;

            ms.push();
            ms.translate((float) (lp.x - cam.x), (float) (lp.y - cam.y) + y, (float) (lp.z - cam.z));
            Matrix4f m = ms.peek().getPositionMatrix();
            float R = 0.26f * size, r = 0.028f * size;
            tube(vc, m, R, r * 1.9f, rgb, 0.16f);   // delikatna poświata
            tube(vc, m, R, r, rgb, 0.98f);          // rdzeń
            ms.pop();
        }
        if (vc != null && vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(WorldFx.flat());
    }
}
