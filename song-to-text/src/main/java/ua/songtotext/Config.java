package ua.songtotext;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public enum FontStyle { MINECRAFT, NEON, PLAIN }
    public enum DisplayMode { CHUNKS, FULL_LINES }

    public boolean enabled = true;
    public boolean mode3d = true;
    public boolean outline = true;
    public boolean hideOnPause = false;
    public boolean darkTheme = true;     // тема меню: тёмная / светлая
    public int color = 0xFF69B4;
    public FontStyle font = FontStyle.NEON;
    public int size = 100;               // 50..300 %
    public double syncOffset = 0.0;      // секунди
    /** Режим показу: CHUNKS = шматки по 3-6 слів, FULL_LINES = повні рядки. */
    public DisplayMode display = DisplayMode.CHUNKS;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("songtotext.json"); }

    public static Config load() {
        try {
            if (Files.exists(path())) {
                Config c = GSON.fromJson(Files.readString(path()), Config.class);
                if (c != null) return c;
            }
        } catch (Exception ignored) { }
        return new Config();
    }

    public void save() {
        try { Files.writeString(path(), GSON.toJson(this)); } catch (Exception ignored) { }
    }
}
