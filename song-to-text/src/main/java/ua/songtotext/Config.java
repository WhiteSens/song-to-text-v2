package ua.songtotext;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public enum FontStyle { MINECRAFT, NEON, PLAIN, IMPACT, SCRIPT, MONO, SERIF }
    public enum AnimStyle { SIMPLE, POP, SLIDE, ZOOM, TYPE }
    public enum DisplayMode { CHUNKS, FULL_LINES }

    public boolean enabled = true;
    public boolean mode3d = true;
    public boolean outline = true;
    public boolean hideOnPause = false;
    public boolean darkTheme = true;     // тема меню: тёмная / светлая
    public int color = 0xFF69B4;
    public FontStyle font = FontStyle.NEON;
    public static final int SIZE_MIN = 10, SIZE_MAX = 100;
    public int size = 100;               // 10..100 %
    public double syncOffset = 0.0;      // секунди
    public static final int DIST_MIN = 1, DIST_MAX = 100;
    public int distance = 6;             // відстань 3D-тексту від гравця, блоків (1..100)
    /** Режим показу: CHUNKS = шматки по 3-6 слів, FULL_LINES = повні рядки. */
    public DisplayMode display = DisplayMode.CHUNKS;
    /** Анімація появи тексту. */
    public AnimStyle anim = AnimStyle.POP;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("songtotext.json"); }

    public static Config load() {
        try {
            if (Files.exists(path())) {
                Config c = GSON.fromJson(Files.readString(path()), Config.class);
                if (c != null) {
                    c.distance = Math.max(DIST_MIN, Math.min(DIST_MAX, c.distance == 0 ? 6 : c.distance));
                    c.size = Math.max(SIZE_MIN, Math.min(SIZE_MAX, c.size));
                    if (c.font == null) c.font = FontStyle.NEON;
                    if (c.anim == null) c.anim = AnimStyle.POP;
                    if (c.display == null) c.display = DisplayMode.CHUNKS;
                    return c;
                }
            }
        } catch (Exception ignored) { }
        return new Config();
    }

    public void save() {
        try { Files.writeString(path(), GSON.toJson(this)); } catch (Exception ignored) { }
    }
}
