package ua.songtotext;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Шрифти тексту: індекс = Config.FontStyle.ordinal(). Беруться з тих, що встановлені в системі (Windows). */
public final class Fonts {
    public static final String[] NAMES = {"Minecraft", "Неон", "Обычный", "Жирный", "Рукописный", "Моно", "Элегантный"};
    private static final String[][] FAMILIES = {
            {"Segoe UI", "Arial"},                                            // Minecraft (в меню — для прев'ю)
            {"Segoe UI Black", "Arial Black", "Segoe UI"},                    // Неон
            {"Segoe UI Light", "Segoe UI", "Arial"},                          // Обычный (тонкий)
            {"Impact", "Arial Black", "Segoe UI"},                            // Жирный
            {"Segoe Script", "Brush Script MT", "Comic Sans MS", "Segoe UI"}, // Рукописный
            {"Consolas", "Courier New", "Monospaced"},                        // Моно
            {"Georgia", "Times New Roman", "Serif"}                           // Элегантный
    };
    private static final int[] STYLES = {Font.PLAIN, Font.BOLD | Font.ITALIC, Font.PLAIN, Font.PLAIN, Font.PLAIN, Font.BOLD, Font.ITALIC};
    private static Set<String> installed;
    private static final Map<Integer, String> chosen = new HashMap<>();

    private Fonts() { }

    public static synchronized String family(int i) {
        String f = chosen.get(i);
        if (f != null) return f;
        if (installed == null) {
            installed = new HashSet<>();
            try { installed.addAll(java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())); }
            catch (Throwable ignored) { }
        }
        f = "SansSerif";
        for (String c : FAMILIES[i]) if (installed.contains(c)) { f = c; break; }
        chosen.put(i, f);
        return f;
    }

    public static Font awt(int i, float size) {
        return new Font(family(i), STYLES[i], 1).deriveFont(size);
    }
}
