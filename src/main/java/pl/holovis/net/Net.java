package pl.holovis.net;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Połączenie z bazą Firebase Realtime Database (REST) z anonimowym logowaniem.
 * Adres bazy i klucz API wpisuje się w NetConfig.
 */
public final class Net {
    private Net() {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Gson GSON = new Gson();

    private static String idToken, refreshToken;
    private static long expiresAt;
    public static volatile String lastError = "";

    public static boolean configured() { return !NetConfig.DB_URL.isEmpty() && !NetConfig.API_KEY.isEmpty(); }

    private static JsonObject postJson(String url, String body, String type) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            String msg = res.body();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"").matcher(msg);
            throw new IllegalStateException("logowanie: " + (m.find() ? m.group(1) : "HTTP " + res.statusCode()));
        }
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    /** Ważny token logowania (anonimowe konto Firebase, odświeżane co godzinę). */
    private static synchronized String token() throws Exception {
        long now = System.currentTimeMillis();
        if (idToken != null && now < expiresAt - 60_000) return idToken;
        JsonObject o;
        if (refreshToken != null) {
            o = postJson("https://securetoken.googleapis.com/v1/token?key=" + NetConfig.API_KEY,
                    "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8),
                    "application/x-www-form-urlencoded");
            idToken = o.get("id_token").getAsString();
            refreshToken = o.get("refresh_token").getAsString();
            expiresAt = now + Long.parseLong(o.get("expires_in").getAsString()) * 1000L;
        } else {
            o = postJson("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + NetConfig.API_KEY,
                    "{\"returnSecureToken\":true}", "application/json");
            idToken = o.get("idToken").getAsString();
            refreshToken = o.get("refreshToken").getAsString();
            expiresAt = now + Long.parseLong(o.get("expiresIn").getAsString()) * 1000L;
        }
        return idToken;
    }

    private static String url(String path, String extra) throws Exception {
        String base = NetConfig.DB_URL.endsWith("/") ? NetConfig.DB_URL.substring(0, NetConfig.DB_URL.length() - 1) : NetConfig.DB_URL;
        return base + "/" + path + ".json?auth=" + token() + (extra == null ? "" : "&" + extra);
    }

    private static JsonElement send(String method, String path, Object body, String extra) {
        if (!configured()) return null;
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(path, extra))).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json");
            String json = body == null ? null : (body instanceof String s ? s : GSON.toJson(body));
            switch (method) {
                case "GET" -> b.GET();
                case "DELETE" -> b.DELETE();
                default -> b.method(method, HttpRequest.BodyPublishers.ofString(json == null ? "null" : json));
            }
            HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) { lastError = res.statusCode() == 401 ? "brak uprawnień (Rules)" : "HTTP " + res.statusCode(); return null; }
            lastError = "";
            return JsonParser.parseString(res.body());
        } catch (Exception e) {
            lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return null;
        }
    }

    public static JsonElement get(String path) { return send("GET", path, null, null); }
    public static JsonElement getShallow(String path) { return send("GET", path, null, "shallow=true"); }
    public static JsonElement put(String path, Object body) { return send("PUT", path, body, null); }
    public static JsonElement patch(String path, Object body) { return send("PATCH", path, body, null); }
    public static void delete(String path) { send("DELETE", path, null, null); }

    /** Dodaje element z automatycznym kluczem; zwraca klucz albo null. */
    public static String push(String path, Object body) {
        JsonElement e = send("POST", path, body, null);
        if (e != null && e.isJsonObject() && e.getAsJsonObject().has("name")) return e.getAsJsonObject().get("name").getAsString();
        return null;
    }
}
