package pl.holovis.net;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import org.lwjgl.glfw.GLFW;
import pl.holovis.Gfx;
import pl.holovis.HoloConfig;
import pl.holovis.gui.QuickReplyScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Powiadomienia o wiadomościach w prawym górnym rogu: główka, nick i treść.
 * Po otwarciu czatu (T) pod wiadomością są przyciski: Przeczytane, Ignoruj, Szybka odpowiedź.
 */
public final class MessageToasts {
    private MessageToasts() {}

    private static final int W = 210, H = 46, GAP = 6, MAX = 3;

    private static final class Toast {
        final KosaNet.Incoming in;
        float slide = 0f;          // 0 = poza ekranem po prawej, 1 = na miejscu
        boolean leaving;
        Toast(KosaNet.Incoming in) { this.in = in; }
    }

    private static final List<Toast> LIST = new ArrayList<>();
    private static long lastNs = System.nanoTime();
    private static boolean wasDown;

    public static void add(KosaNet.Incoming in) {
        for (Toast t : LIST) if (t.in.id().equals(in.id())) return;
        LIST.add(new Toast(in));
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            var ev = net.minecraft.registry.Registries.SOUND_EVENT.get(net.minecraft.util.Identifier.of("minecraft", "block.note_block.pling"));
            if (ev != null) mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(ev, 1.6f, 0.6f));
        } catch (Exception ignored) {}
    }

    private static SkinTextures skin(String nick, String uuid) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(nick);
            if (e != null) return e.getSkinTextures();
        }
        try { return DefaultSkinHelper.getSkinTextures(UUID.fromString(uuid)); } catch (Exception e) { return DefaultSkinHelper.getSkinTextures(UUID.randomUUID()); }
    }

    private static List<String> wrap(TextRenderer tr, String s, int w, int maxLines) {
        List<String> out = new ArrayList<>();
        String rest = s;
        while (!rest.isEmpty() && out.size() < maxLines) {
            String part = tr.trimToWidth(rest, w);
            if (part.isEmpty()) break;
            if (part.length() < rest.length()) {
                int sp = part.lastIndexOf(' ');
                if (sp > 4) part = part.substring(0, sp);
            }
            rest = rest.substring(part.length()).trim();
            if (!rest.isEmpty() && out.size() == maxLines - 1) part = tr.trimToWidth(part, w - 8) + "...";
            out.add(part);
        }
        return out;
    }

    /** HUD (bez otwartego czatu): same powiadomienia. */
    public static void renderHud(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof ChatScreen) return;
        draw(ctx, -1, -1, false, ctx.getScaledWindowWidth());
    }

    /** Otwarty czat: powiadomienia z przyciskami. */
    public static void renderChat(Screen screen, DrawContext ctx, int mx, int my, float delta) {
        draw(ctx, mx, my, true, screen.width);
    }

    private static void draw(DrawContext c, int mx, int my, boolean interactive, int sw) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long ns = System.nanoTime();
        float dt = Math.min(0.1f, (ns - lastNs) / 1_000_000_000f);
        lastNs = ns;
        boolean down = interactive && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean click = down && !wasDown;
        wasDown = down;
        if (LIST.isEmpty()) return;
        TextRenderer tr = mc.textRenderer;
        int accent = HoloConfig.get().guiColor.rgb();
        int y = 8;
        List<Toast> remove = new ArrayList<>();
        int shown = 0;
        for (Toast t : new ArrayList<>(LIST)) {
            if (shown >= MAX && !t.leaving) break;
            float goal = t.leaving ? 0f : 1f;
            t.slide += (goal - t.slide) * Math.min(1f, dt * 9f);
            if (t.leaving && t.slide < 0.02f) { remove.add(t); continue; }
            float e = t.slide;
            int h = H + (interactive ? 20 : 0);
            int x = sw - 8 - W + (int) ((1f - e) * (W + 16));
            float a = Math.min(1f, e * 1.4f);

            Gfx.rrect(c, x - 1, y - 1, W + 2, h + 2, 9, Gfx.argb(accent, 0.55f * a));
            Gfx.rrect(c, x, y, W, h, 8, Gfx.argb(0x0E121A, 0.95f * a));
            Gfx.rboxH(c, x, y, 70, h, 8, Gfx.argb(accent, 0.16f * a), Gfx.argb(accent, 0f));
            if (a > 0.5f) PlayerSkinDrawer.draw(c, skin(t.in.fromMc(), t.in.from()), x + 7, y + 7, 22);
            String who = t.in.fromName().isEmpty() ? t.in.fromMc() : t.in.fromName();
            Gfx.text(c, tr, tr.trimToWidth(who, W - 80), x + 36, y + 7, Gfx.argb(0xFFFFFF, a), false);
            String nick = "@" + t.in.fromMc();
            Gfx.text(c, tr, nick, x + W - 8 - tr.getWidth(nick), y + 7, Gfx.argb(0x7C8598, a), false);
            List<String> lines = wrap(tr, t.in.text(), W - 46, 2);
            for (int i = 0; i < lines.size(); i++) Gfx.text(c, tr, lines.get(i), x + 36, y + 19 + i * 10, Gfx.argb(0xC8D0E0, a), false);

            if (interactive && e > 0.9f && !t.leaving) {
                int by = y + H - 2, bw = (W - 16 - 8) / 3;
                String[] labels = {"Przeczytane", "Ignoruj", "Odpowiedz"};
                int[] cols = {0x2F8CFF, 0x5A3340, accent};
                for (int i = 0; i < 3; i++) {
                    int bx = x + 8 + i * (bw + 4);
                    boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + 16;
                    Gfx.rrect(c, bx, by, bw, 16, 5, Gfx.argb(cols[i], hov ? 0.95f : 0.6f));
                    String l = tr.trimToWidth(labels[i], bw - 4);
                    Gfx.text(c, tr, l, bx + (bw - tr.getWidth(l)) / 2, by + 4, 0xFFFFFFFF, false);
                    if (click && hov) {
                        if (i == 0) { KosaNet.markRead(t.in); t.leaving = true; }
                        else if (i == 1) { KosaNet.ignore(t.in); t.leaving = true; }
                        else {
                            KosaNet.markRead(t.in);
                            t.leaving = true;
                            KosaNet.Incoming in = t.in;
                            mc.execute(() -> mc.setScreen(new QuickReplyScreen(in)));
                        }
                    }
                }
            }
            y += h + GAP;
            shown++;
        }
        LIST.removeAll(remove);
    }
}
