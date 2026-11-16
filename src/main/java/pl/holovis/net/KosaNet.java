package pl.holovis.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import pl.holovis.Accounts;
import pl.holovis.CustomNametag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Konta online KosaVisuals (Firebase Realtime Database).
 * Każde konto ma stały identyfikator (Accounts.id()), więc znajomi i czaty działają między różnymi serwerami.
 *
 *  users/{id}          {acc, mc, mcUuid, server, role, seen, tag}
 *  ranks/{nick}        "VIP" | "FRIEND" | "V-WŁAŚCICIEL"
 *  chats/{chatId}/{m}  {from, fromName, fromMc, to, toName, text, ts, status}
 *  chatmeta/{chatId}   {a, b, last}
 *  inbox/{id}/{m}      {chat, from, fromName, fromMc, text, ts}
 */
public final class KosaNet {
    private KosaNet() {}

    public record User(String id, String acc, String mc, String mcUuid, String server, String role, long seen, CustomNametag.Data tag, String cfg) {
        public String display() { return acc.isEmpty() ? mc : acc; }
    }
    public record Msg(String id, String from, String fromName, String fromMc, String to, String text, long ts, String status) {}
    public record Incoming(String id, String chat, String from, String fromName, String fromMc, String text, long ts) {}
    public record ChatMeta(String id, String a, String b, long last) {}

    private static final ExecutorService EX = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "holovis-net");
        t.setDaemon(true);
        return t;
    });

    private static volatile List<User> users = List.of();
    private static volatile Map<String, String> ranks = Map.of();
    private static volatile Map<String, List<Msg>> chats = Map.of();
    private static volatile List<ChatMeta> allChats = List.of();
    private static final Set<String> seenInbox = new HashSet<>();
    private static volatile String watchedChat;
    private static volatile boolean busy, connected;
    private static long tPresence, tInbox, tUsers, tRanks, tChat;
    private static volatile long serverClockOffset;

    public static boolean available() { return Net.configured() && Accounts.has() && !Accounts.id().isEmpty(); }

    /** true, gdy ostatnie połączenie z bazą się udało. */
    public static boolean connected() { return connected; }

    public static String status() {
        if (!Net.configured()) return "Baza nie jest ustawiona.";
        if (!Accounts.has()) return "Załóż konto, żeby połączyć się z innymi.";
        if (connected) return "";
        String e = Net.lastError;
        return e.isEmpty() ? "Łączenie z bazą..." : "Brak połączenia: " + e;
    }

    public static String myId() { return Accounts.id(); }

    public static String myMcUuid() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) return mc.player.getUuid().toString();
        UUID u = mc.getSession().getUuidOrNull();
        return u == null ? "" : u.toString();
    }

    public static String myMc() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) return mc.player.getGameProfile().getName();
        return mc.getSession().getUsername();
    }

    public static String server() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return "";
        ServerInfo si = mc.getCurrentServerEntry();
        if (si != null && si.address != null) {
            String a = si.address.toLowerCase(Locale.ROOT).trim();
            if (a.endsWith(":25565")) a = a.substring(0, a.length() - 6);
            if (a.endsWith(".")) a = a.substring(0, a.length() - 1);
            return a;
        }
        return mc.isInSingleplayer() ? "singleplayer" : "";
    }

    public static String chatId(String a, String b) { return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a; }

    private static String str(JsonObject o, String k) {
        try { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : ""; } catch (Exception e) { return ""; }
    }
    private static long num(JsonObject o, String k) {
        try { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsLong() : 0L; } catch (Exception e) { return 0L; }
    }
    private static String cut(String s) { return s.length() > 40 ? s.substring(0, 40) : s; }
    private static Map<String, Object> sv() { Map<String, Object> m = new HashMap<>(); m.put(".sv", "timestamp"); return m; }
    private static long now() { return System.currentTimeMillis() + serverClockOffset; }

    // ------------------------------------------------------------------ pętla (co tick klienta)
    public static void tick(MinecraftClient mc) {
        if (!available() || busy) return;
        long now = System.currentTimeMillis();
        boolean presence = now - tPresence > 15_000, inbox = now - tInbox > 3_000, us = now - tUsers > 8_000,
                rk = now - tRanks > 30_000, ch = watchedChat != null && now - tChat > 1_500;
        if (!(presence || inbox || us || rk || ch)) return;
        if (presence) tPresence = now;
        if (inbox) tInbox = now;
        if (us) tUsers = now;
        if (rk) tRanks = now;
        if (ch) tChat = now;
        String me = myId(), srv = server(), acc = Accounts.name(), nick = myMc(), mcu = myMcUuid();
        String role = Accounts.isLocalOwner() ? "owner" : (Accounts.isOwner() ? "vowner" : "");
        String wc = watchedChat;
        CustomNametag.Data myTag = mc.player != null ? CustomNametag.mine() : null;
        String myCfg = presence ? pl.holovis.HoloConfig.exportCode() : "";
        busy = true;
        EX.execute(() -> {
            try {
                if (presence) {
                    Map<String, Object> u = new LinkedHashMap<>();
                    u.put("acc", acc); u.put("mc", nick); u.put("mcUuid", mcu); u.put("server", srv); u.put("role", role); u.put("seen", sv());
                    if (myCfg != null && !myCfg.isEmpty() && myCfg.length() < 4000) u.put("cfg", myCfg);
                    if (myTag != null) {
                        Map<String, Object> t = new LinkedHashMap<>();
                        t.put("l1", myTag.l1()); t.put("l2", myTag.l2()); t.put("icon", myTag.icon());
                        t.put("size", Math.round(myTag.size() * 10)); t.put("font", myTag.font()); t.put("color", myTag.color());
                        t.put("mode", myTag.mode()); t.put("h", Math.round(myTag.height() * 100));
                        if (!myTag.img().isEmpty()) t.put("img", myTag.img());
                        u.put("tag", t);
                    }
                    JsonElement r = Net.put("users/" + me, u);
                    connected = r != null;
                    if (r != null && r.isJsonObject()) {
                        long s = num(r.getAsJsonObject(), "seen");
                        if (s > 0) serverClockOffset = s - System.currentTimeMillis();
                    }
                }
                if (rk) loadRanks();
                if (us) loadUsers();
                if (inbox) loadInbox(me);
                if (wc != null) loadChat(wc);
            } catch (Exception e) {
                Net.lastError = String.valueOf(e.getMessage());
            } finally {
                busy = false;
            }
        });
    }

    private static void loadRanks() {
        JsonElement e = Net.get("ranks");
        if (e == null) return;
        Map<String, String> m = new HashMap<>();
        if (e.isJsonObject())
            for (var en : e.getAsJsonObject().entrySet()) if (en.getValue().isJsonPrimitive()) m.put(en.getKey(), en.getValue().getAsString());
        ranks = m;
    }

    private static void loadUsers() {
        JsonElement e = Net.get("users");
        if (e == null) { connected = false; return; }
        connected = true;
        List<User> l = new ArrayList<>();
        if (e.isJsonObject())
            for (var en : e.getAsJsonObject().entrySet()) {
                if (!en.getValue().isJsonObject()) continue;
                JsonObject o = en.getValue().getAsJsonObject();
                CustomNametag.Data tag = null;
                if (o.has("tag") && o.get("tag").isJsonObject()) {
                    JsonObject t = o.getAsJsonObject("tag");
                    tag = new CustomNametag.Data(cut(str(t, "l1")), cut(str(t, "l2")), (int) num(t, "icon"),
                            Math.max(1f, Math.min(5f, num(t, "size") / 10f)), (int) num(t, "font"), (int) num(t, "color"),
                            (int) num(t, "mode"), t.has("h") ? num(t, "h") / 100f : 0.8f, str(t, "img"));
                }
                l.add(new User(en.getKey(), str(o, "acc"), str(o, "mc"), str(o, "mcUuid"), str(o, "server"), str(o, "role"), num(o, "seen"), tag, str(o, "cfg")));
            }
        users = l;
    }

    private static void loadInbox(String me) {
        JsonElement e = Net.get("inbox/" + me);
        if (e == null || !e.isJsonObject()) return;
        for (var en : e.getAsJsonObject().entrySet()) {
            if (!en.getValue().isJsonObject() || !seenInbox.add(en.getKey())) continue;
            JsonObject o = en.getValue().getAsJsonObject();
            Incoming in = new Incoming(en.getKey(), str(o, "chat"), str(o, "from"), str(o, "fromName"), str(o, "fromMc"),
                    str(o, "text"), num(o, "ts"));
            MinecraftClient.getInstance().execute(() -> MessageToasts.add(in));
        }
    }

    private static void loadChat(String cid) {
        JsonElement e = Net.get("chats/" + cid);
        if (e == null) return;
        List<Msg> l = new ArrayList<>();
        if (e.isJsonObject())
            for (var en : e.getAsJsonObject().entrySet()) {
                if (!en.getValue().isJsonObject()) continue;
                JsonObject o = en.getValue().getAsJsonObject();
                l.add(new Msg(en.getKey(), str(o, "from"), str(o, "fromName"), str(o, "fromMc"), str(o, "to"), str(o, "text"),
                        num(o, "ts"), str(o, "status")));
            }
        l.sort(Comparator.comparing(Msg::id));
        Map<String, List<Msg>> m = new HashMap<>(chats);
        m.put(cid, l);
        chats = m;
    }

    // ------------------------------------------------------------------ dane dla GUI
    public static boolean online(User u) { return now() - u.seen() < 60_000; }

    /** Gracze KosaVisuals na tym samym serwerze co Ty (bez Ciebie). */
    public static List<User> onServer() {
        String me = myId(), srv = server();
        List<User> l = new ArrayList<>();
        if (srv.isEmpty()) return l;
        for (User u : users) if (!u.id().equals(me) && u.server().equals(srv) && online(u)) l.add(u);
        return l;
    }

    /** Twoi znajomi (online najpierw). */
    public static List<User> friends() {
        List<User> l = new ArrayList<>();
        for (User u : users) if (Accounts.isFriend(u.id())) l.add(u);
        l.sort(Comparator.comparing((User u) -> !online(u)).thenComparing(u -> u.display().toLowerCase(Locale.ROOT)));
        return l;
    }

    /** Szuka konta po nazwie konta albo nicku Minecraft. */
    public static User find(String q) {
        String s = q.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        for (User u : users) if (u.acc().toLowerCase(Locale.ROOT).equals(s) || u.mc().toLowerCase(Locale.ROOT).equals(s)) return u.id().equals(myId()) ? null : u;
        return null;
    }

    /** Dla właściciela: wszyscy gracze z zapisanym configiem (online najpierw). */
    public static List<User> withConfigs() {
        List<User> l = new ArrayList<>();
        for (User u : users) if (!u.cfg().isEmpty() && !u.id().equals(myId())) l.add(u);
        l.sort(Comparator.comparing((User u) -> !online(u)).thenComparing(u -> u.display().toLowerCase(Locale.ROOT)));
        return l;
    }

    public static User byId(String id) {
        for (User u : users) if (u.id().equals(id)) return u;
        return null;
    }

    private static User byMcUuid(UUID id) {
        if (id == null) return null;
        String s = id.toString();
        for (User u : users) if (u.mcUuid().equals(s) && now() - u.seen() < 120_000) return u;
        return null;
    }

    /** Czy gracz o tym UUID (w grze) używa KosaVisuals. */
    public static boolean isKosaUser(UUID id) { return byMcUuid(id) != null; }

    /** Napis NameTag innego gracza z KosaVisuals (albo null). */
    public static CustomNametag.Data tagOf(UUID id) {
        User u = byMcUuid(id);
        return u == null ? null : u.tag();
    }

    public static String globalRank(String nick) {
        if (nick == null) return null;
        return ranks.get(nick.toLowerCase(Locale.ROOT));
    }

    public static void setRank(String nick, String rank) {
        String k = nick.toLowerCase(Locale.ROOT);
        Map<String, String> m = new HashMap<>(ranks);
        if (rank == null || rank.isEmpty()) m.remove(k); else m.put(k, rank);
        ranks = m;
        EX.execute(() -> {
            if (rank == null || rank.isEmpty()) Net.delete("ranks/" + k);
            else Net.put("ranks/" + k, "\"" + rank.replace("\"", "") + "\"");
        });
    }

    public static List<Msg> chat(String cid) { List<Msg> l = chats.get(cid); return l == null ? List.of() : l; }

    public static void watch(String cid) { watchedChat = cid; tChat = 0; }

    public static void send(String toId, String toName, String text) {
        String t = text.trim();
        if (t.isEmpty() || !available()) return;
        if (t.length() > 256) t = t.substring(0, 256);
        String me = myId(), cid = chatId(me, toId), acc = Accounts.name(), nick = myMc(), msg = t;
        // od razu widać wiadomość u siebie
        List<Msg> cur = new ArrayList<>(chat(cid));
        cur.add(new Msg("~" + System.nanoTime(), me, acc, nick, toId, msg, System.currentTimeMillis(), "sending"));
        Map<String, List<Msg>> m0 = new HashMap<>(chats);
        m0.put(cid, cur);
        chats = m0;
        EX.execute(() -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", me); m.put("fromName", acc); m.put("fromMc", nick); m.put("to", toId); m.put("toName", toName);
            m.put("text", msg); m.put("ts", sv()); m.put("status", "sent");
            String id = Net.push("chats/" + cid, m);
            if (id != null) {
                Map<String, Object> in = new LinkedHashMap<>();
                in.put("chat", cid); in.put("from", me); in.put("fromName", acc); in.put("fromMc", nick); in.put("text", msg); in.put("ts", sv());
                Net.put("inbox/" + toId + "/" + id, in);
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("a", acc.isEmpty() ? nick : acc); meta.put("b", toName); meta.put("last", sv());
                Net.put("chatmeta/" + cid, meta);
            }
            loadChat(cid);
        });
    }

    public static void markRead(Incoming in) {
        String me = myId();
        EX.execute(() -> {
            Net.patch("chats/" + in.chat() + "/" + in.id(), Map.of("status", "read"));
            Net.delete("inbox/" + me + "/" + in.id());
        });
    }

    public static void ignore(Incoming in) {
        String me = myId();
        EX.execute(() -> Net.delete("inbox/" + me + "/" + in.id()));
    }

    public static List<ChatMeta> allChats() { return allChats; }

    public static void loadAllChats() {
        EX.execute(() -> {
            JsonElement e = Net.get("chatmeta");
            List<ChatMeta> l = new ArrayList<>();
            if (e != null && e.isJsonObject())
                for (var en : e.getAsJsonObject().entrySet()) {
                    if (!en.getValue().isJsonObject()) continue;
                    JsonObject o = en.getValue().getAsJsonObject();
                    l.add(new ChatMeta(en.getKey(), str(o, "a"), str(o, "b"), num(o, "last")));
                }
            l.sort(Comparator.comparingLong(ChatMeta::last).reversed());
            allChats = l;
        });
    }
}
