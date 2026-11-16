package pl.holovis.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import pl.holovis.Gfx;
import pl.holovis.HoloConfig;
import pl.holovis.PostFx;
import pl.holovis.net.KosaNet;

import java.util.ArrayList;
import java.util.List;

/**
 * Prywatny czat KosaVisuals (dymki jak w komunikatorze). Pod Twoimi wiadomościami pojawia się
 * niebieskie "Przeczytano", gdy odbiorca je odczyta. Właściciel może otworzyć dowolną rozmowę tylko do odczytu.
 */
public class KosaChatScreen extends Screen {
    private static final int W = 330, H = 320;
    private final String chatId, partnerUuid, partnerName;
    private final boolean readOnly;
    private final String titleA, titleB;
    private String input = "";
    private int scroll = 0;   // w pikselach od dołu
    private float sc = 1f;
    private int vw, vh;
    private final Screen parent;

    /** Rozmowa z graczem. */
    public KosaChatScreen(Screen parent, String partnerUuid, String partnerName) {
        super(Text.literal("Czat"));
        this.parent = parent;
        this.partnerUuid = partnerUuid;
        this.partnerName = partnerName;
        this.chatId = KosaNet.chatId(KosaNet.myId(), partnerUuid);
        this.readOnly = false;
        this.titleA = partnerName;
        this.titleB = "";
    }

    /** Podgląd cudzej rozmowy (właściciel). */
    public KosaChatScreen(Screen parent, KosaNet.ChatMeta meta) {
        super(Text.literal("Czat"));
        this.parent = parent;
        this.partnerUuid = "";
        this.partnerName = "";
        this.chatId = meta.id();
        this.readOnly = true;
        this.titleA = meta.a();
        this.titleB = meta.b();
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    protected void init() {
        sc = MathHelper.clamp(Math.min((width - 12f) / W, (height - 12f) / H), 0.5f, 1f);
        vw = (int) (width / sc);
        vh = (int) (height / sc);
        KosaNet.watch(chatId);
    }

    @Override
    public void removed() { KosaNet.watch(null); }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }

    private List<String> wrap(String s, int w) {
        List<String> out = new ArrayList<>();
        String rest = s;
        while (!rest.isEmpty()) {
            String part = textRenderer.trimToWidth(rest, w);
            if (part.isEmpty()) break;
            if (part.length() < rest.length()) {
                int sp = part.lastIndexOf(' ');
                if (sp > 2) part = part.substring(0, sp);
            }
            out.add(part);
            rest = rest.substring(part.length()).trim();
        }
        if (out.isEmpty()) out.add("");
        return out;
    }

    @Override
    public void render(DrawContext c, int rawMx, int rawMy, float delta) {
        HoloConfig cfg = HoloConfig.get();
        int accent = cfg.guiColor.rgb();
        PostFx.blurBackground(c, cfg.clientBlur);
        c.fill(0, 0, width, height, 0x66000000);
        c.getMatrices().push();
        c.getMatrices().scale(sc, sc, 1f);
        int x = (vw - W) / 2, y = (vh - H) / 2;

        Gfx.rrect(c, x - 1, y - 1, W + 2, H + 2, 13, Gfx.argb(accent, 0.35f));
        Gfx.rrect(c, x, y, W, H, 12, 0xF510141D);
        Gfx.rboxV(c, x, y, W, 40, 12, Gfx.argb(accent, 0.14f), Gfx.argb(accent, 0f));
        String title = readOnly ? titleA + "  ↔  " + titleB : titleA;
        Gfx.text(c, textRenderer, textRenderer.trimToWidth(title, W - 60), x + 14, y + 12, 0xFFFFFFFF, false);
        String sub = "Podgląd rozmowy (właściciel)";
        if (!readOnly) {
            KosaNet.User pu = KosaNet.byId(partnerUuid);
            sub = pu == null ? "offline" : (KosaNet.online(pu) ? (pu.server().isEmpty() ? "online (w menu)" : "online: " + pu.server()) : "offline");
            boolean fr = pl.holovis.Accounts.isFriend(partnerUuid);
            String fl = fr ? "\u2713 Znajomy" : "+ Znajomy";
            int fw = textRenderer.getWidth(fl) + 12;
            Gfx.rrect(c, x + W - 36 - fw, y + 12, fw, 16, 5, fr ? 0x995CE08A : 0x803A4254);
            Gfx.text(c, textRenderer, fl, x + W - 30 - fw, y + 16, 0xFFFFFFFF, false);
        }
        Gfx.text(c, textRenderer, sub, x + 14, y + 24, 0xFF7C8598, false);
        Gfx.rrect(c, x + W - 30, y + 10, 20, 20, 6, 0x803A4254);
        Gfx.text(c, textRenderer, "x", x + W - 23, y + 16, 0xFFFFFFFF, false);

        // wiadomości od dołu
        int top = y + 42, bottom = y + H - (readOnly ? 22 : 46);
        List<KosaNet.Msg> msgs = KosaNet.chat(chatId);
        String me = KosaNet.myId();
        int cy = bottom - 4 + scroll;
        int maxW = W - 90;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            KosaNet.Msg m = msgs.get(i);
            boolean mine = readOnly ? m.from().equals(msgs.get(0).from()) : m.from().equals(me);
            List<String> lines = wrap(m.text(), maxW);
            int tw = 0;
            for (String l : lines) tw = Math.max(tw, textRenderer.getWidth(l));
            int bh = lines.size() * 10 + 8, bw = tw + 14;
            boolean receipt = !readOnly && mine && "read".equals(m.status()) && (i == msgs.size() - 1 || !msgs.get(i + 1).from().equals(me));
            int extra = receipt ? 11 : 0;
            if (readOnly) extra += 10;
            cy -= bh + extra + 5;
            if (cy + bh + extra < top) break;
            int bx = mine ? x + W - 14 - bw : x + 14;
            if (cy + bh > top && cy < bottom) {
                if (readOnly) {
                    String who = m.fromName().isEmpty() ? m.fromMc() : m.fromName();
                    Gfx.text(c, textRenderer, who, mine ? x + W - 14 - textRenderer.getWidth(who) : x + 14, cy, 0xFF7C8598, false);
                }
                int by = cy + (readOnly ? 10 : 0);
                Gfx.rrect(c, bx, by, bw, bh, 7, mine ? Gfx.argb(accent, 0.85f) : 0xFF262D3C);
                for (int k = 0; k < lines.size(); k++) Gfx.text(c, textRenderer, lines.get(k), bx + 7, by + 5 + k * 10, 0xFFFFFFFF, false);
                if (receipt) Gfx.text(c, textRenderer, "Przeczytano ✓✓", x + W - 14 - textRenderer.getWidth("Przeczytano ✓✓"), by + bh + 2, 0xFF3AA0FF, false);
            }
        }
        // maski górnej i dolnej krawędzi listy
        c.fill(x + 4, y + 40, x + W - 4, top, 0xF510141D);
        if (msgs.isEmpty()) {
            String s = readOnly ? "Brak wiadomości" : "Napisz pierwszą wiadomość do " + partnerName;
            Gfx.text(c, textRenderer, s, x + (W - textRenderer.getWidth(s)) / 2, (top + bottom) / 2, 0xFF5E6678, false);
        }

        if (!readOnly) {
            int iy = y + H - 40;
            Gfx.rrect(c, x + 10, iy, W - 20, 22, 6, 0xE0070A10);
            c.drawBorder(x + 10, iy, W - 20, 22, Gfx.argb(accent, 0.7f));
            String shown = input.isEmpty() ? "Napisz wiadomość... (Enter)" : input + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
            String vis = textRenderer.getWidth(shown) > W - 36 ? textRenderer.trimToWidth(shown, W - 36, true) : shown;
            Gfx.text(c, textRenderer, vis, x + 17, iy + 7, input.isEmpty() ? 0xFF5E6678 : 0xFFFFFFFF, false);
        }
        Gfx.text(c, textRenderer, "Właściciele KosaVisuals mogą czytać czaty.", x + 12, y + H - 13, 0xFF4E5668, false);
        c.getMatrices().pop();
    }

    @Override
    public boolean mouseClicked(double rawX, double rawY, int button) {
        double mx = rawX / sc, my = rawY / sc;
        int x = (vw - W) / 2, y = (vh - H) / 2;
        if (mx >= x + W - 30 && mx < x + W - 10 && my >= y + 10 && my < y + 30) { close(); return true; }
        if (!readOnly) {
            boolean fr = pl.holovis.Accounts.isFriend(partnerUuid);
            int fw = textRenderer.getWidth(fr ? "\u2713 Znajomy" : "+ Znajomy") + 12;
            if (mx >= x + W - 36 - fw && mx < x + W - 36 && my >= y + 12 && my < y + 28) {
                if (fr) pl.holovis.Accounts.removeFriend(partnerUuid); else pl.holovis.Accounts.addFriend(partnerUuid);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, scroll + (int) (verticalAmount * 14));
        return true;
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (readOnly || ch < 32 || input.length() >= 256) return false;
        input += ch;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!readOnly) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) { if (!input.isEmpty()) input = input.substring(0, input.length() - 1); return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (!input.isBlank()) { KosaNet.send(partnerUuid, partnerName, input); input = ""; scroll = 0; }
                return true;
            }
            if (key == GLFW.GLFW_KEY_V && (mods & GLFW.GLFW_MOD_CONTROL) != 0 && client != null) {
                input = (input + client.keyboard.getClipboard()).replace("\n", " ");
                if (input.length() > 256) input = input.substring(0, 256);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }
}
