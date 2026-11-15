package ua.songtotext;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Малює меню окремо від рендера Minecraft — звичайним Java2D (згладжені шрифти, форми, градієнти) —
 * і віддає готову картинку. Minecraft лише показує її як текстуру. Координати — в системі 1280x720.
 * Фон (село, картки, заголовок) береться з еталонних картинок: assets/songtotext/textures/gui/panel_*.png
 */
public final class MenuRenderer {
    // ---- розмітка (спільна з екраном для кліків) ----
    public static final int[] ROW_Y = {173, 253, 333, 413};
    public static final int TOG_X = 490, TOG_W = 88, TOG_H = 44;
    public static final int[][] FONT_BTN = {{903, 1003}, {1011, 1091}, {1099, 1191}};
    public static final int FONT_Y0 = 235, FONT_Y1 = 278;
    public static final int SW_X = 1117, SW_Y = 146, SW_W = 56, SW_H = 54;
    public static final int TH_X = 1010, TH_Y = 50, TH_W = 190, TH_H = 54;
    public static final int PK_X = 930, PK_Y = 208, PK_W = 260, PK_H = 250;
    public static final int SQ_X = 944, SQ_Y = 222, SQ_S = 190;
    public static final int HUE_X = 1148, HUE_W = 26;
    // панель «Дистанція тексту» під карткою
    public static final int DS_X = 72, DS_Y = 478, DS_W = 1132, DS_H = 70, DS_YC = DS_Y + DS_H / 2;
    public static final int DS_TX0 = 500, DS_TX1 = 1040;     // доріжка повзунка

    private static final String[] LEFT = {"Текст на экране", "Режим 3D", "Обводка текста", "Скрывать на паузе"};
    private static final String[] RIGHT = {"Цвет RGB", "Шрифт текста", "Размер текста", "Сдвиг синхронизации"};
    private static final String[] FONT_NAME = {"Minecraft", "Неон", "Обычный"};
    private static final Color TEAL = new Color(0x25C7D9), OFF = new Color(0x4A4A50);

    private static BufferedImage dayBg, nightBg;
    private static String family;

    private MenuRenderer() { }

    private static BufferedImage load(String name) {
        try (InputStream in = MenuRenderer.class.getResourceAsStream("/assets/songtotext/textures/gui/" + name)) {
            return ImageIO.read(in);
        } catch (Exception e) { return null; }
    }

    private static Font font(float size) {
        if (family == null) {
            family = "SansSerif";
            try {
                var names = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
                for (String f : new String[]{"Segoe UI", "Inter", "Roboto", "Arial"}) if (names.contains(f)) { family = f; break; }
            } catch (Throwable ignored) { }
        }
        return new Font(family, Font.PLAIN, 1).deriveFont(size);
    }

    public static BufferedImage render(int pw, int ph, Config cfg, boolean pickerOpen, float[] hsv) {
        if (dayBg == null) dayBg = load("panel_day.png");
        if (nightBg == null) nightBg = load("panel_night.png");
        BufferedImage out = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        BufferedImage bg = cfg.darkTheme ? nightBg : dayBg;
        if (bg != null) g.drawImage(bg, 0, 0, pw, ph, null);
        g.scale(pw / 1280.0, ph / 720.0);

        // ---- ліва колонка ----
        boolean[] v = {cfg.enabled, cfg.mode3d, cfg.outline, cfg.hideOnPause};
        for (int i = 0; i < 4; i++) {
            text(g, LEFT[i], 94, ROW_Y[i], 30, Color.WHITE, false);
            toggle(g, TOG_X, ROW_Y[i] - TOG_H / 2, v[i]);
        }

        // ---- права колонка ----
        text(g, RIGHT[0], 677, ROW_Y[0], 30, Color.WHITE, false);
        swatch(g, cfg.color);
        text(g, RIGHT[1], 677, ROW_Y[1], 30, Color.WHITE, false);
        for (int i = 0; i < 3; i++) fontButton(g, i, cfg.font.ordinal() == i);
        text(g, RIGHT[2], 677, ROW_Y[2], 30, Color.WHITE, false);
        text(g, cfg.size + "%", 1176, ROW_Y[2], 30, Color.WHITE, true);
        text(g, RIGHT[3], 677, ROW_Y[3], 30, Color.WHITE, false);
        text(g, String.format(java.util.Locale.ROOT, "%.1f с", cfg.syncOffset), 1176, ROW_Y[3], 30, Color.WHITE, true);

        distancePanel(g, cfg.distance);
        themeButton(g, cfg.darkTheme);
        if (pickerOpen) picker(g, cfg.color, hsv);
        g.dispose();
        return out;
    }

    // ---------- елементи ----------
    private static void distancePanel(Graphics2D g, int dist) {
        g.setColor(new Color(0x10, 0x12, 0x18, 150));
        g.fill(new RoundRectangle2D.Float(DS_X, DS_Y, DS_W, DS_H, 22, 22));
        g.setColor(new Color(255, 255, 255, 50));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Float(DS_X, DS_Y, DS_W, DS_H, 22, 22));
        text(g, "Дистанция текста (3D)", 94, DS_YC, 30, Color.WHITE, false);
        float t = (dist - Config.DIST_MIN) / (float) (Config.DIST_MAX - Config.DIST_MIN);
        float kx = DS_TX0 + t * (DS_TX1 - DS_TX0);
        g.setColor(OFF);
        g.fill(new RoundRectangle2D.Float(DS_TX0, DS_YC - 5, DS_TX1 - DS_TX0, 10, 10, 10));
        g.setColor(TEAL);
        g.fill(new RoundRectangle2D.Float(DS_TX0, DS_YC - 5, kx - DS_TX0, 10, 10, 10));
        g.setColor(new Color(0x25, 0xC7, 0xD9, 70)); g.fill(new Ellipse2D.Float(kx - 18, DS_YC - 18, 36, 36));
        g.setColor(Color.WHITE); g.fill(new Ellipse2D.Float(kx - 13, DS_YC - 13, 26, 26));
        text(g, dist + (dist % 10 == 1 && dist != 11 ? " блок" : (dist % 10 >= 2 && dist % 10 <= 4 && (dist < 12 || dist > 14) ? " блока" : " блоков")), 1176, DS_YC, 30, Color.WHITE, true);
    }

    private static void text(Graphics2D g, String s, float x, float yc, float size, Color c, boolean right) {
        g.setFont(font(size));
        FontMetrics fm = g.getFontMetrics();
        float w = fm.stringWidth(s);
        if (right) x -= w;
        float by = yc + (fm.getAscent() - fm.getDescent()) / 2f;
        g.setColor(new Color(0, 0, 0, 95)); g.drawString(s, x + 1.5f, by + 2f);
        g.setColor(c); g.drawString(s, x, by);
    }

    private static void toggle(Graphics2D g, int x, int y, boolean on) {
        if (on) { g.setColor(new Color(0x25, 0xC7, 0xD9, 70)); g.fill(new RoundRectangle2D.Float(x - 3, y - 3, TOG_W + 6, TOG_H + 6, TOG_H + 6, TOG_H + 6)); }
        g.setColor(on ? TEAL : OFF);
        g.fill(new RoundRectangle2D.Float(x, y, TOG_W, TOG_H, TOG_H, TOG_H));
        int d = TOG_H - 8, kx = on ? x + TOG_W - d - 4 : x + 4;
        g.setColor(new Color(0, 0, 0, 60)); g.fill(new Ellipse2D.Float(kx, y + 5, d, d));
        g.setColor(on ? Color.WHITE : new Color(0xD0D0D6)); g.fill(new Ellipse2D.Float(kx, y + 4, d, d));
    }

    private static void swatch(Graphics2D g, int rgb) {
        g.setColor(new Color(0x14, 0x14, 0x18, 230));
        g.fill(new RoundRectangle2D.Float(SW_X, SW_Y, SW_W, SW_H, 12, 12));
        Shape inner = new RoundRectangle2D.Float(SW_X + 4, SW_Y + 4, SW_W - 8, SW_H - 8, 8, 8);
        g.setColor(new Color(rgb | 0xFF000000)); g.fill(inner);
        Shape old = g.getClip(); g.setClip(inner);                      // ледь помітна «шашка», як в еталоні
        g.setColor(new Color(255, 255, 255, 38));
        for (int cy = 0; cy < SW_H; cy += 12) for (int cx = 0; cx < SW_W; cx += 12)
            if (((cx / 12) + (cy / 12)) % 2 == 0) g.fillRect(SW_X + 4 + cx, SW_Y + 4 + cy, 12, 12);
        g.setClip(old);
    }

    private static void fontButton(Graphics2D g, int i, boolean sel) {
        int x0 = FONT_BTN[i][0], x1 = FONT_BTN[i][1], y0 = FONT_Y0, h = FONT_Y1 - FONT_Y0;
        Shape r = new RoundRectangle2D.Float(x0, y0, x1 - x0, h, 12, 12);
        g.setColor(sel ? TEAL : new Color(8, 12, 18, 185)); g.fill(r);
        if (!sel) { g.setColor(new Color(255, 255, 255, 95)); g.setStroke(new BasicStroke(1.6f)); g.draw(r); }
        float size = 21;
        g.setFont(font(size));
        while (g.getFontMetrics().stringWidth(FONT_NAME[i]) > (x1 - x0) - 12 && size > 12) { size -= 0.5f; g.setFont(font(size)); }
        FontMetrics fm = g.getFontMetrics();
        float tx = x0 + (x1 - x0 - fm.stringWidth(FONT_NAME[i])) / 2f, ty = y0 + h / 2f + (fm.getAscent() - fm.getDescent()) / 2f;
        g.setColor(Color.WHITE); g.drawString(FONT_NAME[i], tx, ty);
    }

    private static void themeButton(Graphics2D g, boolean dark) {
        Shape r = new RoundRectangle2D.Float(TH_X, TH_Y, TH_W, TH_H, TH_H, TH_H);
        g.setColor(dark ? new Color(10, 16, 26, 170) : new Color(255, 255, 255, 70)); g.fill(r);
        g.setColor(new Color(255, 255, 255, dark ? 90 : 150)); g.setStroke(new BasicStroke(1.8f)); g.draw(r);
        float cx = TH_X + 36, cy = TH_Y + TH_H / 2f;
        if (dark) {                                                     // місяць
            Area moon = new Area(new Ellipse2D.Float(cx - 12, cy - 12, 24, 24));
            moon.subtract(new Area(new Ellipse2D.Float(cx - 4, cy - 17, 22, 22)));
            g.setColor(new Color(0xC9D6FF)); g.fill(moon);
        } else {                                                        // сонце
            g.setColor(new Color(0xFFD23F)); g.fill(new Ellipse2D.Float(cx - 8, cy - 8, 16, 16));
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int a = 0; a < 8; a++) {
                double an = a * Math.PI / 4;
                g.draw(new Line2D.Double(cx + Math.cos(an) * 12, cy + Math.sin(an) * 12, cx + Math.cos(an) * 17, cy + Math.sin(an) * 17));
            }
        }
        text(g, dark ? "Тёмная" : "Светлая", TH_X + 68, TH_Y + TH_H / 2f, 26, Color.WHITE, false);
    }

    private static void picker(Graphics2D g, int rgb, float[] hsv) {
        g.setColor(new Color(0, 0, 0, 90)); g.fill(new RoundRectangle2D.Float(PK_X + 3, PK_Y + 5, PK_W, PK_H, 22, 22));
        Shape box = new RoundRectangle2D.Float(PK_X, PK_Y, PK_W, PK_H, 22, 22);
        g.setColor(new Color(0x14, 0x18, 0x20, 247)); g.fill(box);
        g.setColor(new Color(255, 255, 255, 80)); g.setStroke(new BasicStroke(1.6f)); g.draw(box);

        // квадрат насиченість/яскравість
        Shape sq = new RoundRectangle2D.Float(SQ_X, SQ_Y, SQ_S, SQ_S, 6, 6);
        g.setColor(new Color(Color.HSBtoRGB(hsv[0], 1f, 1f))); g.fill(sq);
        g.setPaint(new GradientPaint(SQ_X, 0, Color.WHITE, SQ_X + SQ_S, 0, new Color(255, 255, 255, 0))); g.fill(sq);
        g.setPaint(new GradientPaint(0, SQ_Y, new Color(0, 0, 0, 0), 0, SQ_Y + SQ_S, Color.BLACK)); g.fill(sq);
        g.setPaint(null);
        float mx = SQ_X + hsv[1] * SQ_S, my = SQ_Y + (1 - hsv[2]) * SQ_S;
        g.setStroke(new BasicStroke(3f)); g.setColor(new Color(0, 0, 0, 150)); g.draw(new Ellipse2D.Float(mx - 8, my - 8, 16, 16));
        g.setStroke(new BasicStroke(2f)); g.setColor(Color.WHITE); g.draw(new Ellipse2D.Float(mx - 8, my - 8, 16, 16));

        // смуга відтінку
        float[] fr = new float[7]; Color[] cs = new Color[7];
        for (int i = 0; i < 7; i++) { fr[i] = i / 6f; cs[i] = new Color(Color.HSBtoRGB(i / 6f, 1f, 1f)); }
        Shape bar = new RoundRectangle2D.Float(HUE_X, SQ_Y, HUE_W, SQ_S, 6, 6);
        g.setPaint(new LinearGradientPaint(0, SQ_Y, 0, SQ_Y + SQ_S, fr, cs)); g.fill(bar); g.setPaint(null);
        float hy = SQ_Y + hsv[0] * SQ_S;
        g.setColor(Color.WHITE);
        g.fill(path(HUE_X - 11, hy - 7, HUE_X - 11, hy + 7, HUE_X - 1, hy));
        g.fill(path(HUE_X + HUE_W + 11, hy - 7, HUE_X + HUE_W + 11, hy + 7, HUE_X + HUE_W + 1, hy));
        g.setStroke(new BasicStroke(2f)); g.draw(new Line2D.Float(HUE_X, hy, HUE_X + HUE_W, hy));

        // попередній перегляд і HEX
        g.setColor(new Color(rgb | 0xFF000000)); g.fill(new RoundRectangle2D.Float(PK_X + 14, 424, 30, 22, 6, 6));
        g.setColor(new Color(255, 255, 255, 120)); g.setStroke(new BasicStroke(1.4f)); g.draw(new RoundRectangle2D.Float(PK_X + 14, 424, 30, 22, 6, 6));
        text(g, String.format("#%06X", rgb & 0xFFFFFF), PK_X + 56, 435, 20, Color.WHITE, false);
    }

    private static Path2D path(float x1, float y1, float x2, float y2, float x3, float y3) {
        Path2D p = new Path2D.Float(); p.moveTo(x1, y1); p.lineTo(x2, y2); p.lineTo(x3, y3); p.closePath(); return p;
    }
}
