package ua.songtotext;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/** Малює рядок пісні справжнім шрифтом (Java2D) з обведенням/світінням у прозору картинку. */
public final class TextRaster {
    private TextRaster() { }

    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static Color lighten(int rgb, float k) {
        int r = rgb >> 16 & 255, gg = rgb >> 8 & 255, b = rgb & 255;
        return new Color((int) (r + (255 - r) * k), (int) (gg + (255 - gg) * k), (int) (b + (255 - b) * k));
    }

    /** @param fi індекс шрифту (Config.FontStyle.ordinal), px — розмір шрифту в пікселях */
    public static BufferedImage render(String text, int fi, int rgb, boolean outline, int px) {
        Font font = Fonts.awt(fi, px);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics(); hints(pg); pg.setFont(font);
        FontMetrics fm = pg.getFontMetrics();
        FontRenderContext frc = pg.getFontRenderContext();
        int pad = Math.max(4, Math.round(px * 0.40f));
        int w = fm.stringWidth(text) + pad * 2, h = fm.getAscent() + fm.getDescent() + pad * 2;
        GlyphVector gv = font.createGlyphVector(frc, text);
        Shape sh = gv.getOutline(pad, pad + fm.getAscent());
        pg.dispose();

        BufferedImage img = new BufferedImage(Math.max(2, w), Math.max(2, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics(); hints(g);
        float u = px / 10f;

        // м'яка тінь
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(AffineTransform.getTranslateInstance(u * 0.4, u * 0.6).createTransformedShape(sh));

        if (fi == Config.FontStyle.NEON.ordinal()) {                 // неон: кольорове світіння + світла заливка
            if (outline) {
                int[] al = {36, 66, 120};
                float[] wd = {u * 3.4f, u * 2.4f, u * 1.4f};
                for (int i = 0; i < 3; i++) {
                    g.setColor(new Color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, al[i]));
                    g.setStroke(new BasicStroke(wd[i], BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(sh);
                }
            }
            g.setColor(lighten(rgb, 0.8f)); g.fill(sh);
        } else if (fi == Config.FontStyle.PLAIN.ordinal()) {         // тонкий білий
            if (outline) { g.setColor(new Color(0, 0, 0, 170)); g.setStroke(new BasicStroke(u * 1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(sh); }
            g.setColor(Color.WHITE); g.fill(sh);
        } else {                                                     // решта: колір + чорний контур
            if (outline) { g.setColor(new Color(0, 0, 0, 225)); g.setStroke(new BasicStroke(u * 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(sh); }
            g.setColor(new Color(rgb | 0xFF000000)); g.fill(sh);
        }
        g.dispose();
        return img;
    }
}
