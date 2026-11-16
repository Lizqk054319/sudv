package pl.holovis;

import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;

/** Aspect Ratio: rozciąga obraz gry do wybranych proporcji (jak 4:3 rozciągnięte w CS2). */
public final class AspectRatio {
    private AspectRatio() {}

    public static final String[] NAMES = {"4:3", "5:4", "16:10", "3:2", "1:1", "21:9", "16:9", "Własne"};
    public static final float[] RATIOS = {4f / 3f, 5f / 4f, 16f / 10f, 3f / 2f, 1f, 21f / 9f, 16f / 9f, 0f};

    public static float target() {
        HoloConfig c = HoloConfig.get();
        if (!c.aspectOn) return 0f;
        int i = Math.max(0, Math.min(RATIOS.length - 1, c.aspectPreset));
        float r = RATIOS[i] > 0f ? RATIOS[i] : c.aspectCustom;
        return Math.max(0.4f, Math.min(3.5f, r));
    }

    /** Zmienia macierz projekcji tak, jakby okno miało inne proporcje niż ma naprawdę. */
    public static void apply(Matrix4f proj) {
        float t = target();
        if (t <= 0f || proj == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        float cur = mc.getWindow().getFramebufferWidth() / (float) Math.max(1, mc.getWindow().getFramebufferHeight());
        if (Math.abs(cur - t) < 0.001f) return;
        proj.m00(proj.m00() * cur / t);
    }
}
