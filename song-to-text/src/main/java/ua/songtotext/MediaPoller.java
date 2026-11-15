package ua.songtotext;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Один окремий потік: тримає PowerShell, що раз на секунду віддає стан медіапанелі Windows. */
public class MediaPoller {
    private volatile MediaState state = MediaState.EMPTY;
    private volatile boolean running;
    private Thread thread;
    private Process process;

    public MediaState state() { return state; }

    public void start() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return; // тільки Windows
        running = true;
        thread = new Thread(this::loop, "SongToText-Poller");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (process != null) process.destroyForcibly();
    }

    private void loop() {
        while (running) {
            try {
                Path script = Files.createTempFile("songtotext-media", ".ps1");
                try (InputStream in = MediaPoller.class.getResourceAsStream("/assets/songtotext/scripts/media.ps1")) {
                    Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
                }
                script.toFile().deleteOnExit();
                process = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                        "-ExecutionPolicy", "Bypass", "-File", script.toString())
                        .redirectErrorStream(false).start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = r.readLine()) != null) parse(line.trim());
                }
            } catch (Exception ignored) { }
            if (running) {
                try { Thread.sleep(3000); } catch (InterruptedException e) { return; } // перезапуск
            }
        }
    }

    private void parse(String line) {
        if (line.isEmpty() || line.charAt(0) != '{') return;
        try {
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            if (!o.has("title")) { state = MediaState.EMPTY; return; }
            long ago = o.get("ago").getAsLong();
            state = new MediaState(str(o, "app"), str(o, "title"), str(o, "artist"),
                    o.get("playing").getAsBoolean(), o.get("pos").getAsLong(), o.get("end").getAsLong(),
                    System.currentTimeMillis() - ago);
        } catch (Exception ignored) { }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }
}
