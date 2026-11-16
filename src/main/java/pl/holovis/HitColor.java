package pl.holovis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.LivingEntity;

import java.lang.reflect.Field;

/**
 * Hit Color: zmienia kolor czerwonego mignięcia, które gra nakłada na model trafionego gracza / moba.
 * Podmieniamy kolor w teksturze "overlay" Minecrafta, więc podświetlenie idealnie pokrywa model
 * (także slime'y, pająki itd.) i trwa dokładnie tyle, co w grze.
 */
public final class HitColor {
    private HitColor() {}

    /** Kolor z gry: czerwony z alfą 0xB2. */
    private static final int VANILLA = 0xB2FF0000;
    private static int applied = VANILLA;
    private static Field texField;
    private static boolean broken;

    private static NativeImageBackedTexture texture(OverlayTexture ov) throws IllegalAccessException {
        if (texField == null) {
            for (Field f : OverlayTexture.class.getDeclaredFields()) {
                if (NativeImageBackedTexture.class.isAssignableFrom(f.getType())) { f.setAccessible(true); texField = f; break; }
            }
            if (texField == null) { broken = true; return null; }
        }
        return (NativeImageBackedTexture) texField.get(ov);
    }

    /** Co tick: wgrywa wybrany kolor do tekstury (tylko gdy się zmienił). */
    public static void sync() {
        if (broken) return;
        HoloConfig c = HoloConfig.get();
        int want = VANILLA;
        if (c.hitColor) {
            int a = Math.round(Math.max(0.15f, Math.min(1f, c.hitColorAlpha)) * 255f);
            want = (a << 24) | (c.hitColorColor.rgb() & 0xFFFFFF);
        }
        if (want == applied) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.gameRenderer == null) return;
        try {
            NativeImageBackedTexture tex = texture(mc.gameRenderer.getOverlayTexture());
            if (tex == null) return;
            NativeImage img = tex.getImage();
            if (img == null) return;
            for (int y = 0; y < 8; y++)
                for (int x = 0; x < 16; x++) img.setColorArgb(x, y, want);
            tex.upload();
            applied = want;
        } catch (Throwable t) {
            broken = true;
            System.err.println("[KosaVisuals] Hit Color: nie udało się zmienić koloru: " + t);
        }
    }

    // Stare wywołania (zostawione, żeby reszta kodu się nie zmieniała) - kolor robi teraz sama gra.
    public static void onAttack(LivingEntity e) {}
    public static void flash(LivingEntity e) {}
    public static void tick() {}
    public static void render(WorldRenderContext ctx) {}
}
