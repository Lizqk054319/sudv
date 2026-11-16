package pl.holovis;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.SimpleOption;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Optymalizacja (/kosa optymalizacja): obniża ustawienia Minecrafta i wyłącza ciężkie efekty KosaVisuals.
 * Poziomy: Medium, Hard, Ultra, Extreme. Niczego nie podnosi. Oryginalne wartości zapisuje do pliku,
 * więc "Przywróć" działa także po restarcie gry i przywraca stan sprzed pierwszej optymalizacji.
 */
public final class Optimizer {
    private Optimizer() {}

    public static final String[] LEVELS = {"Medium", "Hard", "Ultra", "Extreme"};
    private static final Gson GSON = new Gson();
    private static final Path BACKUP = FabricLoader.getInstance().getConfigDir().resolve("holovis").resolve("optimizer_backup.json");
    private static Map<String, String> backup;

    private static Map<String, String> backup() {
        if (backup != null) return backup;
        backup = new LinkedHashMap<>();
        try {
            if (Files.exists(BACKUP)) {
                Map<String, String> m = GSON.fromJson(Files.readString(BACKUP), new TypeToken<Map<String, String>>() {}.getType());
                if (m != null) backup.putAll(m);
            }
        } catch (Exception ignored) {}
        return backup;
    }

    private static void saveBackup() {
        try {
            Files.createDirectories(BACKUP.getParent());
            Files.writeString(BACKUP, GSON.toJson(backup()));
        } catch (Exception ignored) {}
    }

    public static boolean canRestore() { return !backup().isEmpty(); }

    // ------------------------------------------------------------------ opcje Minecrafta
    private record Opt(String key, String label, SimpleOption<?> option) {}

    private static List<Opt> options(GameOptions o) {
        List<Opt> l = new ArrayList<>();
        l.add(new Opt("viewDistance", "Zasięg renderowania", o.getViewDistance()));
        l.add(new Opt("simulationDistance", "Zasięg symulacji", o.getSimulationDistance()));
        l.add(new Opt("graphics", "Grafika", o.getGraphicsMode()));
        l.add(new Opt("clouds", "Chmury", o.getCloudRenderMode()));
        l.add(new Opt("particles", "Cząsteczki", o.getParticles()));
        l.add(new Opt("entityShadows", "Cienie encji", o.getEntityShadows()));
        l.add(new Opt("biomeBlend", "Mieszanie biomów", o.getBiomeBlendRadius()));
        l.add(new Opt("entityDistance", "Odległość encji", o.getEntityDistanceScaling()));
        l.add(new Opt("vsync", "VSync", o.getEnableVsync()));
        l.add(new Opt("maxFps", "Limit FPS", o.getMaxFps()));
        l.add(new Opt("menuBlur", "Rozmycie tła menu", o.getMenuBackgroundBlurriness()));
        l.add(new Opt("ao", "Gładkie oświetlenie", o.getAo()));
        l.add(new Opt("chunkBuilder", "Budowanie chunków", o.getChunkBuilderMode()));
        return l;
    }

    private static String ser(Object v) { return v instanceof Enum<?> e ? e.name() : String.valueOf(v); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setFrom(SimpleOption opt, String s) {
        Object cur = opt.getValue();
        Object v;
        if (cur instanceof Integer) v = Integer.parseInt(s);
        else if (cur instanceof Double) v = Double.parseDouble(s);
        else if (cur instanceof Boolean) v = Boolean.parseBoolean(s);
        else if (cur instanceof Enum<?> e) v = Enum.valueOf((Class) e.getDeclaringClass(), s);
        else return;
        opt.setValue(v);
    }

    @SuppressWarnings("unchecked")
    private static <T> T named(SimpleOption<T> o, String name) {
        T cur = o.getValue();
        if (!(cur instanceof Enum<?> e)) return cur;
        for (Object x : e.getDeclaringClass().getEnumConstants()) if (((Enum<?>) x).name().equals(name)) return (T) x;
        return cur;
    }

    private static String show(String key, Object v) {
        String n = ser(v);
        return switch (key) {
            case "viewDistance", "simulationDistance" -> n + " ch.";
            case "entityDistance" -> Math.round(Double.parseDouble(n) * 100) + "%";
            case "maxFps" -> Integer.parseInt(n) >= 260 ? "Bez limitu" : n;
            case "entityShadows", "vsync", "ao" -> Boolean.parseBoolean(n) ? "wł." : "wył.";
            case "graphics" -> n.equals("FAST") ? "Szybka" : n.equals("FANCY") ? "Dokładna" : "Najlepsza";
            case "clouds" -> n.equals("OFF") ? "Wył." : n.equals("FAST") ? "Szybkie" : "Dokładne";
            case "particles" -> n.equals("MINIMAL") ? "Minimalne" : n.equals("DECREASED") ? "Zmniejszone" : "Wszystkie";
            case "chunkBuilder" -> n.equals("NONE") ? "Wątki (szybkie)" : "Blokujące";
            default -> n;
        };
    }

    /** Docelowa wartość opcji dla poziomu albo null (bez zmian). */
    private static Object target(String key, SimpleOption<?> o, int L) {
        Object cur = o.getValue();
        switch (key) {
            case "viewDistance": { int t = new int[]{10, 7, 4, 2}[L]; return (Integer) cur > t ? t : null; }
            case "simulationDistance": { int t = new int[]{8, 6, 5, 5}[L]; return (Integer) cur > t ? t : null; }
            case "graphics": return ((Enum<?>) cur).name().equals("FAST") ? null : GraphicsMode.FAST;
            case "clouds": {
                CloudRenderMode t = L == 0 ? CloudRenderMode.FAST : CloudRenderMode.OFF;
                return ((Enum<?>) cur).ordinal() > t.ordinal() ? t : null;
            }
            case "particles": {
                Object t = named(o, L == 0 ? "DECREASED" : "MINIMAL");
                return ((Enum<?>) cur).ordinal() < ((Enum<?>) t).ordinal() ? t : null;
            }
            case "entityShadows", "vsync": return Boolean.TRUE.equals(cur) ? Boolean.FALSE : null;
            case "biomeBlend": { int t = new int[]{2, 0, 0, 0}[L]; return (Integer) cur > t ? t : null; }
            case "entityDistance": { double t = new double[]{0.75, 0.6, 0.5, 0.5}[L]; return (Double) cur > t + 0.001 ? t : null; }
            case "maxFps": return (Integer) cur < 260 ? 260 : null;
            case "menuBlur": return (Integer) cur > 0 ? 0 : null;
            case "ao": return L >= 1 && Boolean.TRUE.equals(cur) ? Boolean.FALSE : null;
            case "chunkBuilder": { Object t = named(o, "NONE"); return L >= 1 && cur != t ? t : null; }
            default: return null;
        }
    }

    // ------------------------------------------------------------------ efekty KosaVisuals
    private record Fx(String field, String label, int level) {}
    private static final Fx[] FX = {
            new Fx("motionBlur", "Motion Blur", 0), new Fx("worldParticles", "World Particles", 0),
            new Fx("colorGrade", "Color Saturation", 1), new Fx("jumpCircles", "Jump Circles", 1), new Fx("halo", "Halo", 1),
            new Fx("skyEnabled", "Custom Sky", 2), new Fx("targetEsp", "Target ESP", 2),
            new Fx("dmgNumbers", "Damage Numbers", 3), new Fx("killFx", "Kill Effect", 3)};

    private static Field field(String n) {
        try { return HoloConfig.class.getField(n); } catch (Exception e) { return null; }
    }

    // ------------------------------------------------------------------ uruchomienie
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<String> run(int level, boolean apply, boolean kosaFx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        GameOptions o = mc.options;
        int L = Math.max(0, Math.min(3, level));
        List<String> out = new ArrayList<>();
        Map<String, String> bk = backup();

        for (Opt op : options(o)) {
            if (op.option() == null) continue;
            Object t;
            try { t = target(op.key(), op.option(), L); } catch (Exception e) { t = null; }
            if (t == null) continue;
            out.add(op.label() + ": " + show(op.key(), op.option().getValue()) + " → " + show(op.key(), t));
            if (apply) {
                bk.putIfAbsent("mc:" + op.key(), ser(op.option().getValue()));
                ((SimpleOption) op.option()).setValue(t);
            }
        }

        HoloConfig c = HoloConfig.get();
        if (kosaFx) {
            for (Fx fx : FX) {
                if (fx.level() > L) continue;
                Field f = field(fx.field());
                try {
                    if (f == null || !f.getBoolean(c)) continue;
                    out.add("KosaVisuals: " + fx.label() + " → wył.");
                    if (apply) { bk.putIfAbsent("kv:" + fx.field(), "true"); f.setBoolean(c, false); }
                } catch (Exception ignored) {}
            }
            if (L >= 1 && c.clientBlur > 0f) {
                out.add("KosaVisuals: Rozmycie klienta → 0");
                if (apply) { bk.putIfAbsent("kvf:clientBlur", String.valueOf(c.clientBlur)); c.clientBlur = 0f; }
            }
        }

        if (apply) {
            saveBackup();
            o.write();
            HoloConfig.save();
            if (mc.worldRenderer != null) mc.worldRenderer.reload();
            System.gc();
        }
        return out;
    }

    /** Przywraca ustawienia Minecrafta i efekty KosaVisuals sprzed pierwszej optymalizacji. */
    @SuppressWarnings({"rawtypes"})
    public static int restore() {
        Map<String, String> bk = backup();
        if (bk.isEmpty()) return 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        int n = 0;
        for (Opt op : options(mc.options)) {
            String v = bk.get("mc:" + op.key());
            if (v == null || op.option() == null) continue;
            try { setFrom((SimpleOption) op.option(), v); n++; } catch (Exception ignored) {}
        }
        HoloConfig c = HoloConfig.get();
        for (Map.Entry<String, String> e : bk.entrySet()) {
            try {
                if (e.getKey().startsWith("kv:")) { Field f = field(e.getKey().substring(3)); if (f != null) { f.setBoolean(c, true); n++; } }
                if (e.getKey().equals("kvf:clientBlur")) { c.clientBlur = Float.parseFloat(e.getValue()); n++; }
            } catch (Exception ignored) {}
        }
        bk.clear();
        saveBackup();
        mc.options.write();
        HoloConfig.save();
        if (mc.worldRenderer != null) mc.worldRenderer.reload();
        return n;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Windows: podnosi priorytet procesu Minecrafta do "Wysoki". */
    public static void highPriority() {
        if (!isWindows()) return;
        long pid = ProcessHandle.current().pid();
        Thread t = new Thread(() -> {
            try {
                new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                        "(Get-Process -Id " + pid + ").PriorityClass = 'High'").redirectErrorStream(true).start().waitFor();
            } catch (Exception e) {
                System.err.println("[KosaVisuals] Nie udało się zmienić priorytetu: " + e);
            }
        }, "holovis-priority");
        t.setDaemon(true);
        t.start();
    }
}
