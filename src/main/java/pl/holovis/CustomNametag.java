package pl.holovis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import pl.holovis.net.KosaNet;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * NameTag: własny napis (1-2 linie, ikonka, kolor, czcionka, wysokość) nad głową. Widzisz go u siebie (F5),
 * a gdy konta online działają - widzą go też inni gracze z KosaVisuals.
 * Ikonka "Własna" to Twój obrazek PNG z config/holovis/nametag_icon.png (max 12 KB).
 */
public final class CustomNametag {
    private CustomNametag() {}

    public static final String[] ICONS = {"Play", "Live", "Czat", "Aparat", "Nutka", "Pad", "Serce", "Gwiazda", "Korona", "Ogień", "Własna"};
    public static final int CUSTOM = 10;
    public static final String[] FONTS = {"Minecraft", "Pogrubiona", "Kursywa"};
    private static final Identifier FONT = Identifier.of("holovis", "kosa");
    public static final Path ICON_FILE = HoloSounds.DIR.getParent().resolve("nametag_icon.png");
    private static final int MAX_BYTES = 12_000;

    /** Dane napisu (własne albo innego gracza z bazy). img = obrazek PNG w base64 (ikonka "Własna"). */
    public record Data(String l1, String l2, int icon, float size, int font, int color, int mode, float height, String img) {}

    // ------------------------------------------------------------------ własny obrazek
    private static long selfMtime = -2;
    private static String selfB64 = "";
    public static String selfError = "";
    private static final Map<String, Identifier> TEX = new HashMap<>();

    /** Wczytuje config/holovis/nametag_icon.png, gdy się zmienił. */
    public static void refreshSelf() {
        long mt = -1;
        try { if (Files.exists(ICON_FILE)) mt = Files.getLastModifiedTime(ICON_FILE).toMillis(); } catch (Exception ignored) {}
        if (mt == selfMtime) return;
        selfMtime = mt;
        selfB64 = "";
        selfError = "";
        if (mt < 0) { selfError = "Brak pliku nametag_icon.png"; return; }
        try {
            byte[] b = Files.readAllBytes(ICON_FILE);
            if (b.length > MAX_BYTES) { selfError = "Obrazek za duży (max 12 KB, np. 64x64)"; return; }
            if (!(b.length > 8 && b[0] == (byte) 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G')) { selfError = "To nie jest plik PNG"; return; }
            selfB64 = Base64.getEncoder().encodeToString(b);
        } catch (Exception e) {
            selfError = "Nie udało się wczytać obrazka";
        }
    }

    public static String selfImage() { refreshSelf(); return selfB64; }

    /** Tekstura z obrazka base64 (z pamięci podręcznej) albo null. */
    public static Identifier texture(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        String key = Integer.toHexString(b64.hashCode()) + "_" + b64.length();
        Identifier id = TEX.get(key);
        if (id != null) return id;
        try {
            byte[] b = Base64.getDecoder().decode(b64);
            NativeImage img = NativeImage.read(new ByteArrayInputStream(b));
            id = Identifier.of("holovis", "nt_icon_" + key.toLowerCase());
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
            if (TEX.size() > 64) TEX.clear();
            TEX.put(key, id);
            return id;
        } catch (Exception e) {
            TEX.put(key, Identifier.of("holovis", "textures/misc/white.png"));
            return null;
        }
    }

    public static Data mine() {
        HoloConfig c = HoloConfig.get();
        if (!c.ntOn) return null;
        if (c.ntLine1.isBlank() && (c.ntMode == 0 || c.ntLine2.isBlank()) && c.ntIcon < 0) return null;
        String img = c.ntIcon == CUSTOM ? selfImage() : "";
        return new Data(c.ntLine1, c.ntLine2, c.ntIcon, c.ntSize, c.ntFont, c.ntColor.rgb(), c.ntMode, c.ntHeight, img);
    }

    /** Linia tekstu z ikonką z czcionki (ikonka "Własna" jest rysowana osobno). */
    public static Text line(String s, int icon, int font, int color) {
        MutableText t = Text.empty();
        if (icon >= 0 && icon < CUSTOM) {
            t.append(Text.literal(String.valueOf((char) (0xE010 + icon))).setStyle(Style.EMPTY.withFont(FONT).withColor(0xFFFFFF)));
            if (!s.isEmpty()) t.append(Text.literal(" "));
        }
        Style st = Style.EMPTY.withColor(color & 0xFFFFFF).withBold(font == 1).withItalic(font == 2);
        t.append(Text.literal(s).setStyle(st));
        return t;
    }

    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        Vec3d cam = ctx.camera().getPos();
        float td = ctx.tickCounter().getTickDelta(false);
        boolean any = false;
        for (PlayerEntity p : mc.world.getPlayers()) {
            Data d;
            if (p == mc.player) {
                if (mc.options.getPerspective().isFirstPerson()) continue;
                d = mine();
            } else {
                d = KosaNet.tagOf(p.getUuid());
            }
            if (d == null || p.isInvisible()) continue;
            Vec3d lp = p.getLerpedPos(td);
            if (lp.squaredDistanceTo(cam) > 48 * 48) continue;
            float h = Math.max(0.1f, Math.min(3f, d.height()));
            draw(mc.textRenderer, ms, vcp, d, (float) (lp.x - cam.x), (float) (lp.y - cam.y) + p.getHeight() + h, (float) (lp.z - cam.z), ctx);
            any = true;
        }
        if (any && vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw();
    }

    private static void icon(VertexConsumer vc, Matrix4f m, float x, float y, float s, int light) {
        vc.vertex(m, x, y, 0f).color(0xFFFFFFFF).texture(0f, 0f).light(light);
        vc.vertex(m, x, y + s, 0f).color(0xFFFFFFFF).texture(0f, 1f).light(light);
        vc.vertex(m, x + s, y + s, 0f).color(0xFFFFFFFF).texture(1f, 1f).light(light);
        vc.vertex(m, x + s, y, 0f).color(0xFFFFFFFF).texture(1f, 0f).light(light);
    }

    private static void drawLine(TextRenderer tr, Matrix4f m, VertexConsumerProvider vcp, Text t, Identifier img, float y, int light) {
        int bg = 0x50000000;
        float w = tr.getWidth(t) + (img != null ? 11 : 0);
        float x = -w / 2f;
        if (img != null) {
            icon(vcp.getBuffer(RenderLayer.getText(img)), m, x, y - 0.5f, 9f, light);
            x += 11;
        }
        tr.draw(t, x, y, 0xFFFFFFFF, false, m, vcp, TextRenderer.TextLayerType.NORMAL, bg, light);
    }

    private static void draw(TextRenderer tr, MatrixStack ms, VertexConsumerProvider vcp, Data d, float x, float y, float z, WorldRenderContext ctx) {
        float s = 0.0125f * Math.max(1f, Math.min(5f, d.size()));
        ms.push();
        ms.translate(x, y, z);
        ms.multiply(ctx.camera().getRotation());
        ms.scale(s, -s, s);
        Matrix4f m = ms.peek().getPositionMatrix();
        int light = 0xF000F0;
        Identifier img = d.icon() == CUSTOM ? texture(d.img()) : null;
        if (d.mode() == 1 && !d.l2().isBlank()) {
            drawLine(tr, m, vcp, line(d.l2(), -1, d.font(), d.color()), null, -10f, light);
            drawLine(tr, m, vcp, line(d.l1(), d.icon(), d.font(), d.color()), img, -21f, light);
        } else {
            drawLine(tr, m, vcp, line(d.l1(), d.icon(), d.font(), d.color()), img, -10f, light);
        }
        ms.pop();
    }
}
