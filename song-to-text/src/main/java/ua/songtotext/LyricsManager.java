package ua.songtotext;

import com.google.gson.JsonArray;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Завантажує синхронізований текст з LRCLIB (без ключів) і віддає поточний «cue». */
public class LyricsManager {
    public record Cue(long startMs, long endMs, String text, int id) { }

    private static final Pattern TS = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)]");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SongToText-Lyrics"); t.setDaemon(true); return t;
    });

    private volatile List<Cue> chunks = List.of();
    private volatile List<Cue> lines = List.of();
    private volatile String loadedKey = "";
    private volatile int version = 0;

    public String loadedKey() { return loadedKey; }

    private record Entry(List<Cue> lines, List<Cue> chunks, long at) { }
    private final java.util.Map<String, Entry> cache = new java.util.concurrent.ConcurrentHashMap<>();

    private static String normKey(MediaState s) { return (s.artist() + "|" + s.title()).toLowerCase(); }

    public void request(MediaState s) {
        final int my = ++version;
        loadedKey = s.key();
        final String nk = normKey(s);
        Entry e = cache.get(nk);
        // той самий трек уже завантажено (напр. після перемотування) — віддаємо миттєво
        if (e != null && (!e.lines().isEmpty() || System.currentTimeMillis() - e.at() < 60000)) {
            lines = e.lines(); chunks = e.chunks(); return;
        }
        chunks = List.of(); lines = List.of();
        exec.submit(() -> {
            try {
                String lrc = fetch(s.artist(), s.title());
                if (lrc == null) { cache.put(nk, new Entry(List.of(), List.of(), System.currentTimeMillis())); return; }
                build(lrc, my, nk);
            } catch (Exception ignored) { }
        });
    }

    public Cue cueAt(double sec, Config.DisplayMode mode) {
        long ms = (long) (sec * 1000);
        for (Cue c : (mode == Config.DisplayMode.CHUNKS ? chunks : lines)) {
            if (ms >= c.startMs() && ms < c.endMs()) return c;
        }
        return null;
    }

    // ---------- мережа ----------
    /** Пробує кілька варіантів «виконавець / назва» (SoundCloud часто віддає їх нечітко). */
    private String fetch(String artist, String title) throws Exception {
        List<String[]> cands = new ArrayList<>();
        addCand(cands, artist, title);
        addCand(cands, cleanArtist(artist), cleanTitle(title));
        addCand(cands, stripAccents(cleanArtist(artist)), cleanTitle(title));
        int d = title.indexOf(" - ");                       // «Артист - Назва» прямо в назві
        if (d > 0) {
            String l = title.substring(0, d), r = title.substring(d + 3);
            addCand(cands, cleanArtist(l), cleanTitle(r));
            addCand(cands, cleanArtist(r), cleanTitle(l));
        }
        for (String[] c : cands) {
            if (c[0].isBlank()) continue;
            String r = get("https://lrclib.net/api/get?artist_name=" + enc(c[0]) + "&track_name=" + enc(c[1]));
            if (r != null) { String l = synced(JsonParser.parseString(r)); if (l != null) return log(c, l); }
        }
        for (String[] c : cands) {
            String r = get("https://lrclib.net/api/search?track_name=" + enc(c[1]) + (c[0].isBlank() ? "" : "&artist_name=" + enc(c[0])));
            String l = firstSynced(r);
            if (l != null) return log(c, l);
        }
        for (String[] c : cands) {
            String l = firstSynced(get("https://lrclib.net/api/search?q=" + enc((c[0] + " " + c[1]).trim())));
            if (l != null) return log(c, l);
        }
        String l = firstSynced(get("https://lrclib.net/api/search?q=" + enc(cleanTitle(title))));   // лише назва
        if (l != null) return log(new String[]{"", cleanTitle(title)}, l);
        SongToTextClient.LOGGER.info("[Song to text] текст не знайдено: {} - {}", artist, title);
        return null;
    }

    private static String log(String[] c, String lrc) {
        SongToTextClient.LOGGER.info("[Song to text] текст знайдено: {} - {}", c[0], c[1]);
        return lrc;
    }

    private static void addCand(List<String[]> list, String artist, String title) {
        artist = artist == null ? "" : artist.trim(); title = title == null ? "" : title.trim();
        if (title.isBlank()) return;
        for (String[] c : list) if (c[0].equalsIgnoreCase(artist) && c[1].equalsIgnoreCase(title)) return;
        list.add(new String[]{artist, title});
    }

    static String stripAccents(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private String get(String url) {
        try {
            HttpResponse<String> resp = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8)).header("User-Agent", "SongToText-Fabric/1.0").GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (Exception e) { return null; }
    }

    private static String firstSynced(String body) {
        if (body == null) return null;
        JsonElement e = JsonParser.parseString(body);
        if (!e.isJsonArray()) return null;
        JsonArray arr = e.getAsJsonArray();
        for (JsonElement it : arr) { String l = synced(it); if (l != null) return l; }
        return null;
    }

    private static String synced(JsonElement e) {
        if (!e.isJsonObject()) return null;
        JsonObject o = e.getAsJsonObject();
        if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()) {
            String s = o.get("syncedLyrics").getAsString();
            if (!s.isBlank()) return s;
        }
        return null;
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    static String cleanTitle(String t) {
        return t.replaceAll("(?i)\\s*\\|\\s*soundcloud.*$", "").replaceAll("\\s*[(\\[].*?[)\\]]", "").replaceAll("(?i)\\s*-\\s*(remaster(ed)?|official|live).*$", "").trim();
    }

    static String cleanArtist(String a) {
        return a.replaceAll("(?i)\\s*-\\s*topic$", "").split("\\s*(,|&| x | feat\\.?| ft\\.?)\\s*")[0].trim();
    }

    // ---------- парсинг ----------
    private record Raw(long ms, String text) { }

    private void build(String lrc, int my, String nk) {
        List<Raw> raw = new ArrayList<>();
        for (String line : lrc.split("\\R")) {
            Matcher m = TS.matcher(line);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find()) {
                times.add((long) ((Integer.parseInt(m.group(1)) * 60 + Double.parseDouble(m.group(2))) * 1000));
                end = m.end();
            }
            String text = line.substring(end).trim();
            for (long t : times) raw.add(new Raw(t, text));
        }
        raw.sort((x, y) -> Long.compare(x.ms(), y.ms()));

        List<Cue> L = new ArrayList<>(), C = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Raw r = raw.get(i);
            if (r.text().isBlank()) continue;
            long next = i + 1 < raw.size() ? raw.get(i + 1).ms() : r.ms() + 6000;
            long lineEnd = Math.min(next, r.ms() + 8000);
            L.add(new Cue(r.ms(), lineEnd, r.text(), i * 100));

            String[] w = r.text().split("\\s+");
            int n = w.length;
            int parts = n <= 6 ? 1 : (int) Math.ceil(n / 5.0);   // шматки по 3–6 слів
            long span = Math.min(next, r.ms() + 6000) - r.ms();
            for (int p = 0; p < parts; p++) {
                int a = p * n / parts, b = (p + 1) * n / parts;
                long s = r.ms() + span * p / parts;
                long e = p == parts - 1 ? Math.min(next, r.ms() + 6000) : r.ms() + span * (p + 1) / parts;
                C.add(new Cue(s, e, String.join(" ", java.util.Arrays.copyOfRange(w, a, b)), i * 100 + p));
            }
        }
        cache.put(nk, new Entry(L, C, System.currentTimeMillis()));
        if (my == version) { lines = L; chunks = C; }
    }
}
