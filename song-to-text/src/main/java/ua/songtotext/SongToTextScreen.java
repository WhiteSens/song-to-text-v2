package ua.songtotext;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static ua.songtotext.MenuRenderer.*;

/**
 * Екран меню. Сам інтерфейс малює MenuRenderer (Java2D) у картинку; тут вона лише показується
 * як текстура 1:1 до пікселів і обробляються кліки (координати перераховуються в систему 1280x720).
 */
public class SongToTextScreen extends Screen {
    private static final Identifier TEX_ID = Identifier.of(SongToTextClient.MOD_ID, "menu_dynamic");
    private static final int[] PRESETS = {0xFF69B4, 0x25C7D9, 0xFFD23F, 0x7CFC00, 0xB388FF, 0xFF5252, 0xFFFFFF};

    private final Screen parent;
    private NativeImageBackedTexture tex;
    private int texW, texH, gx, gy, gw, gh;
    private boolean dirty = true, pickerOpen = false, dragSV = false, dragHue = false, dragDist = false;
    private final float[] hsv = new float[3];

    public SongToTextScreen(Screen parent) {
        super(Text.literal("Song to text"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int c = SongToTextClient.config.color;
        Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, hsv);
    }

    @Override public void close() { SongToTextClient.config.save(); client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        if (tex != null) { client.getTextureManager().destroyTexture(TEX_ID); tex = null; }
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        c.fill(0, 0, width, height, 0x66000000);
        double sf = client.getWindow().getScaleFactor();
        float fit = Math.min(width / 1280f, height / 720f) * 0.96f / 1.5f;      // менший у 1.5 раза
        gw = Math.max(64, (int) (1280 * fit)); gh = gw * 720 / 1280;
        gx = (width - gw) / 2; gy = (height - gh) / 2;
        int pw = (int) Math.round(gw * sf), ph = (int) Math.round(gh * sf);

        if (tex == null || pw != texW || ph != texH) {
            if (tex != null) client.getTextureManager().destroyTexture(TEX_ID);
            texW = pw; texH = ph;
            tex = new NativeImageBackedTexture(() -> "songtotext_menu", new NativeImage(NativeImage.Format.RGBA, texW, texH, false));
            client.getTextureManager().registerTexture(TEX_ID, tex);
            dirty = true;
        }
        if (dirty) { paint(); dirty = false; }
        c.drawTexture(RenderPipelines.GUI_TEXTURED, TEX_ID, gx, gy, 0f, 0f, gw, gh, texW, texH, texW, texH);
    }

    private void paint() {
        BufferedImage bi = MenuRenderer.render(texW, texH, SongToTextClient.config, pickerOpen, hsv);
        int[] px = bi.getRGB(0, 0, texW, texH, null, 0, texW);
        NativeImage img = tex.getImage();
        for (int y = 0; y < texH; y++) for (int x = 0; x < texW; x++) img.setColorArgb(x, y, px[y * texW + x]);
        tex.upload();
    }

    // ---------- ввід ----------
    private double rx(double mx) { return (mx - gx) * 1280.0 / gw; }
    private double ry(double my) { return (my - gy) * 720.0 / gh; }
    private static boolean in(double x, double y, int x0, int y0, int x1, int y1) { return x >= x0 && x <= x1 && y >= y0 && y <= y1; }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double x = rx(click.x()), y = ry(click.y());
        int btn = click.button();
        Config cfg = SongToTextClient.config;

        if (pickerOpen) {
            if (in(x, y, SQ_X, SQ_Y, SQ_X + SQ_S, SQ_Y + SQ_S)) { dragSV = true; pickSV(x, y); return true; }
            if (in(x, y, HUE_X - 12, SQ_Y - 4, HUE_X + HUE_W + 12, SQ_Y + SQ_S + 4)) { dragHue = true; pickHue(y); return true; }
            if (in(x, y, PK_X, PK_Y, PK_X + PK_W, PK_Y + PK_H)) return true;
            pickerOpen = false; cfg.save(); dirty = true;                       // клік повз палітру — закрити
            if (!in(x, y, SW_X, SW_Y, SW_X + SW_W, SW_Y + SW_H)) return true;
            return true;
        }
        if (btn == 0 && in(x, y, DS_TX0 - 25, DS_Y, DS_TX1 + 25, DS_Y + DS_H)) { dragDist = true; pickDist(x); return true; }
        if (btn == 0 && in(x, y, TH_X, TH_Y, TH_X + TH_W, TH_Y + TH_H)) { cfg.darkTheme = !cfg.darkTheme; cfg.save(); dirty = true; return true; }
        for (int i = 0; i < 4; i++) {
            if (btn == 0 && in(x, y, TOG_X, ROW_Y[i] - TOG_H / 2, TOG_X + TOG_W, ROW_Y[i] + TOG_H / 2)) {
                switch (i) { case 0 -> cfg.enabled = !cfg.enabled; case 1 -> cfg.mode3d = !cfg.mode3d;
                    case 2 -> cfg.outline = !cfg.outline; default -> cfg.hideOnPause = !cfg.hideOnPause; }
                cfg.save(); dirty = true; return true;
            }
        }
        if (btn == 0 && in(x, y, SW_X, SW_Y, SW_X + SW_W, SW_Y + SW_H)) {
            int c = cfg.color; Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, hsv);
            pickerOpen = true; dirty = true; return true;
        }
        for (int i = 0; i < 3; i++) {
            if (in(x, y, FONT_BTN[i][0], FONT_Y0, FONT_BTN[i][1], FONT_Y1)) { cfg.font = Config.FontStyle.values()[i]; cfg.save(); dirty = true; return true; }
        }
        int dir = btn == 1 ? -1 : 1;
        if (in(x, y, 1090, ROW_Y[2] - 24, 1200, ROW_Y[2] + 24)) { step(0, dir); return true; }
        if (in(x, y, 1090, ROW_Y[3] - 24, 1200, ROW_Y[3] + 24)) { step(1, dir); return true; }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragSV) { pickSV(rx(click.x()), ry(click.y())); return true; }
        if (dragHue) { pickHue(ry(click.y())); return true; }
        if (dragDist) { pickDist(rx(click.x())); return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragSV || dragHue || dragDist) { dragSV = dragHue = dragDist = false; SongToTextClient.config.save(); return true; }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        double x = rx(mouseX), y = ry(mouseY);
        int dir = v > 0 ? 1 : -1;
        if (in(x, y, DS_X, DS_Y, DS_X + DS_W, DS_Y + DS_H)) {
            Config cfg = SongToTextClient.config;
            cfg.distance = Math.max(Config.DIST_MIN, Math.min(Config.DIST_MAX, cfg.distance + dir));
            cfg.save(); dirty = true; return true;
        }
        if (in(x, y, 677, ROW_Y[2] - 24, 1200, ROW_Y[2] + 24)) { step(0, dir); return true; }
        if (in(x, y, 677, ROW_Y[3] - 24, 1200, ROW_Y[3] + 24)) { step(1, dir); return true; }
        return false;
    }

    private void pickDist(double x) {
        double t = Math.max(0, Math.min(1, (x - DS_TX0) / (DS_TX1 - DS_TX0)));
        SongToTextClient.config.distance = Config.DIST_MIN + (int) Math.round(t * (Config.DIST_MAX - Config.DIST_MIN));
        dirty = true;
    }

    private void pickSV(double x, double y) {
        hsv[1] = (float) Math.max(0, Math.min(1, (x - SQ_X) / SQ_S));
        hsv[2] = (float) Math.max(0, Math.min(1, 1 - (y - SQ_Y) / SQ_S));
        applyColor();
    }

    private void pickHue(double y) {
        hsv[0] = (float) Math.max(0, Math.min(0.999, (y - SQ_Y) / SQ_S));
        applyColor();
    }

    private void applyColor() {
        SongToTextClient.config.color = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
        dirty = true;
    }

    private void step(int what, int dir) {
        Config cfg = SongToTextClient.config;
        if (what == 0) cfg.size = Math.max(50, Math.min(300, cfg.size + 10 * dir));
        else cfg.syncOffset = Math.max(-10, Math.min(10, Math.round((cfg.syncOffset + 0.1 * dir) * 10) / 10.0));
        cfg.save(); dirty = true;
    }
}
