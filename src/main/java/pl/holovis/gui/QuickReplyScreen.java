package pl.holovis.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import pl.holovis.Gfx;
import pl.holovis.HoloConfig;
import pl.holovis.net.KosaNet;

/** Szybka odpowiedź: małe okienko z polem tekstu, Enter wysyła wiadomość do nadawcy. */
public class QuickReplyScreen extends Screen {
    private final KosaNet.Incoming to;
    private String text = "";
    private static final int W = 260, H = 74;

    public QuickReplyScreen(KosaNet.Incoming to) { super(Text.literal("Szybka odpowiedź")); this.to = to; }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        int accent = HoloConfig.get().guiColor.rgb();
        int x = (width - W) / 2, y = height - H - 40;
        Gfx.rrect(c, x - 1, y - 1, W + 2, H + 2, 10, Gfx.argb(accent, 0.6f));
        Gfx.rrect(c, x, y, W, H, 9, 0xF20E121A);
        String who = to.fromName().isEmpty() ? to.fromMc() : to.fromName();
        Gfx.text(c, textRenderer, textRenderer.trimToWidth("Odpowiedz: " + who, W - 20), x + 10, y + 9, 0xFFFFFFFF, false);
        Gfx.text(c, textRenderer, textRenderer.trimToWidth("„" + to.text() + "”", W - 20), x + 10, y + 21, 0xFF7C8598, false);
        Gfx.rrect(c, x + 8, y + 36, W - 16, 20, 5, 0xE0070A10);
        c.drawBorder(x + 8, y + 36, W - 16, 20, Gfx.argb(accent, 0.8f));
        String shown = text + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        String vis = shown.length() > 0 && textRenderer.getWidth(shown) > W - 28 ? textRenderer.trimToWidth(shown, W - 28, true) : shown;
        Gfx.text(c, textRenderer, vis, x + 14, y + 42, 0xFFFFFFFF, false);
        Gfx.text(c, textRenderer, "Enter - wyślij, Esc - anuluj", x + 10, y + H - 12, 0xFF5E6678, false);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (ch >= 32 && text.length() < 256) { text += ch; return true; }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_BACKSPACE) { if (!text.isEmpty()) text = text.substring(0, text.length() - 1); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (!text.isBlank()) KosaNet.send(to.from(), to.fromName().isEmpty() ? to.fromMc() : to.fromName(), text);
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_V && (mods & GLFW.GLFW_MOD_CONTROL) != 0 && client != null) {
            text = (text + client.keyboard.getClipboard()).replace("\n", " ");
            if (text.length() > 256) text = text.substring(0, 256);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
