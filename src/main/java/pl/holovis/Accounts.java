package pl.holovis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Konto KosaVisuals (lokalne): nazwa, opcjonalnie konto właściciela (kod) i rangi nadawane graczom.
 * Wszystko zapisuje się tylko na tym komputerze (config/holovis/account.json) - bez serwera mod nie może
 * wysłać rang innym graczom ani sprawdzić, kto jeszcze używa KosaVisuals.
 */
public final class Accounts {
    private Accounts() {}

    public static final String[] RANKS = {"", "VIP", "FRIEND", "V-WŁAŚCICIEL"};
    public static final int[] RANK_COLORS = {0xFFFFFF, 0xFFD23C, 0xFF7AC8, 0xFF8A2A};

    /** Skróty SHA-256 kodów właściciela (same kody nie są zapisane w kodzie źródłowym). */
    private static final String[] OWNER_HASHES = {
            "07c2bab71ae959b843be1ac8cfab9c53b64dfe22dd1ed57317efc35ff10ec446",
            "c3781c48dafa7375ca21a8ecaf076e74b505845a520a887e7c8824c983e44c33"};

    private static final class Data {
        String name = "";
        /** Stały identyfikator konta KosaVisuals (ten sam na każdym serwerze). */
        String id = "";
        long created;
        List<String> friends = new ArrayList<>();
        boolean owner;
        Map<String, String> ranks = new LinkedHashMap<>();
        List<String> seen = new ArrayList<>();
    }

    /** Konta przypisane do konta Minecraft (UUID), żeby skopiowany config nie dawał komuś cudzego konta. */
    private static final class Store { Map<String, Data> accounts = new LinkedHashMap<>(); }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("holovis").resolve("accounts.json");
    private static Store store;
    private static long lastScan;

    /** UUID zalogowanego konta Minecraft (albo nick, gdy UUID brak). */
    public static String mcId() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSession() == null) return "unknown";
        java.util.UUID u = mc.getSession().getUuidOrNull();
        return u != null ? u.toString() : "name:" + mc.getSession().getUsername().toLowerCase(Locale.ROOT);
    }

    private static Store store() {
        if (store != null) return store;
        store = new Store();
        try {
            if (Files.exists(PATH)) {
                Store s = GSON.fromJson(Files.readString(PATH), Store.class);
                if (s != null && s.accounts != null) store = s;
            }
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Nie udało się wczytać kont: " + e);
        }
        return store;
    }

    private static Data d() {
        Data x = store().accounts.computeIfAbsent(mcId(), k -> new Data());
        if (x.ranks == null) x.ranks = new LinkedHashMap<>();
        if (x.seen == null) x.seen = new ArrayList<>();
        if (x.name == null) x.name = "";
        if (x.friends == null) x.friends = new ArrayList<>();
        if ((x.id == null || x.id.isEmpty()) && !x.name.isEmpty()) { x.id = java.util.UUID.randomUUID().toString(); save(); }
        return x;
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(store()));
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Nie udało się zapisać kont: " + e);
        }
    }

    public static boolean has() { return !d().name.isEmpty(); }
    public static String id() { return d().id == null ? "" : d().id; }

    // ------------------------------------------------------------------ znajomi (identyfikatory kont KosaVisuals)
    public static List<String> friends() { return d().friends; }
    public static boolean isFriend(String accId) { return d().friends.contains(accId); }
    public static void addFriend(String accId) { if (!d().friends.contains(accId)) { d().friends.add(accId); save(); } }
    public static void removeFriend(String accId) { if (d().friends.remove(accId)) save(); }
    public static String name() { return d().name; }
    /** Właściciel z kodu (na tym komputerze). */
    public static boolean isLocalOwner() { return has() && d().owner; }

    /** Właściciel albo V-Właściciel (ranga nadana przez właściciela, widoczna w bazie online). */
    public static boolean isOwner() {
        if (isLocalOwner()) return true;
        if (!has()) return false;
        return "V-WŁAŚCICIEL".equals(pl.holovis.net.KosaNet.globalRank(pl.holovis.net.KosaNet.myMc()));
    }

    /** @return null gdy OK, inaczej komunikat */
    public static String create(String name) {
        String n = name == null ? "" : name.trim();
        if (n.length() < 3) return "Nazwa musi mieć min. 3 znaki";
        if (n.length() > 16) n = n.substring(0, 16);
        d().name = n;
        d().id = java.util.UUID.randomUUID().toString();
        d().created = System.currentTimeMillis();
        save();
        return null;
    }

    public static void delete() {
        store().accounts.remove(mcId());
        save();
    }

    private static String sha(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** @return true, gdy kod jest poprawny (konto staje się kontem właściciela) */
    public static boolean redeem(String code) {
        // wielkość liter nie ma znaczenia, spacje na końcach są ignorowane
        String h = sha(code == null ? "" : code.trim().toLowerCase(Locale.ROOT));
        for (String o : OWNER_HASHES) {
            if (o.equals(h)) {
                d().owner = true;
                save();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ rangi i gracze
    public static String rank(String player) {
        if (player == null) return "";
        if (pl.holovis.net.Net.configured()) {
            String g = pl.holovis.net.KosaNet.globalRank(player);
            if (g != null) return g;
        }
        String r = d().ranks.get(player.toLowerCase(Locale.ROOT));
        return r == null ? "" : r;
    }

    public static int rankColor(String rank) {
        for (int i = 0; i < RANKS.length; i++) if (RANKS[i].equals(rank)) return RANK_COLORS[i];
        return 0xFFFFFF;
    }

    public static void setRank(String player, String rank) {
        String k = player.toLowerCase(Locale.ROOT);
        if (rank == null || rank.isEmpty()) d().ranks.remove(k); else d().ranks.put(k, rank);
        save();
        if (pl.holovis.net.KosaNet.available()) pl.holovis.net.KosaNet.setRank(player, rank);
    }

    /** Gracze spotkani na serwerach (z listy TAB), najnowsi na końcu. */
    public static List<String> seen() { return d().seen; }

    /** Co kilka sekund zapamiętuje graczy z listy TAB. */
    public static void tick(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        if (now - lastScan < 5000 || mc.getNetworkHandler() == null) return;
        lastScan = now;
        boolean changed = false;
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            String n = e.getProfile().getName();
            if (n == null || n.isEmpty()) continue;
            List<String> s = d().seen;
            if (!s.contains(n)) {
                s.add(n);
                if (s.size() > 300) s.remove(0);
                changed = true;
            }
        }
        if (changed) save();
    }
}
