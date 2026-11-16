package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Kosa Nametag: odznaka "K" (kolorowy znaczek z białą literą) po lewej stronie nicku - nad głową i w TAB.
 * Dodatkowo rangi z konta (VIP, FRIEND), widoczne lokalnie.
 * Mod działa tylko po stronie klienta, więc odznakę widzisz u siebie i u bota UwU_Lizaczek.
 */
public final class KosaTag {
    private KosaTag() {}

    public static final String MARK = "holovis_kosa_tag";
    private static final Identifier FONT = Identifier.of("holovis", "kosa");

    /** Czy ten tekst już ma nasze dodatki (żeby nie dodać ich dwa razy). */
    public static boolean has(Text t) {
        if (t == null || t.getSiblings().isEmpty()) return false;
        return MARK.equals(t.getSiblings().get(0).getStyle().getInsertion());
    }

    public static boolean enabled() { return HoloConfig.get().kosaTag; }

    private static boolean user(UUID id, PlayerEntity p) {
        if (!enabled()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return false;
        if (p != null) return p == mc.player || GhostBot.isBot(p) || pl.holovis.net.KosaNet.isKosaUser(p.getUuid());
        if (id == null) return false;
        return id.equals(mc.player.getUuid()) || pl.holovis.net.KosaNet.isKosaUser(id);
    }

    public static boolean isUser(UUID id) { return user(id, null); }
    public static boolean isUser(PlayerEntity p) { return user(null, p); }

    /** Odznaka K (+ ewentualna ranga) przed nickiem, albo ten sam tekst, gdy nic nie trzeba dodawać. */
    public static Text decorate(boolean kosaUser, String playerName, Text name) {
        if (name == null || has(name)) return name;
        String rank = Accounts.rank(playerName);
        if (!kosaUser && rank.isEmpty()) return name;
        MutableText out = Text.empty();
        // pierwszy element zawsze ma znacznik MARK
        out.append(Text.literal("").setStyle(Style.EMPTY.withInsertion(MARK)));
        if (kosaUser) {
            int rgb = HoloConfig.get().kosaTagColor.rgb();
            out.append(Text.literal("").setStyle(Style.EMPTY.withFont(FONT).withColor(rgb)));
            out.append(Text.literal("").setStyle(Style.EMPTY.withFont(FONT)));
            out.append(Text.literal("").setStyle(Style.EMPTY.withFont(FONT).withColor(0xFFFFFF)));
            out.append(Text.literal("").setStyle(Style.EMPTY.withFont(FONT)));
        }
        if (!rank.isEmpty()) {
            out.append(Text.literal(rank).setStyle(Style.EMPTY.withColor(Accounts.rankColor(rank)).withBold(true)));
            out.append(Text.literal(" "));
        }
        return out.append(name);
    }

    public static Text decorate(PlayerEntity p, Text name) {
        return decorate(isUser(p), p.getGameProfile().getName(), name);
    }
}
