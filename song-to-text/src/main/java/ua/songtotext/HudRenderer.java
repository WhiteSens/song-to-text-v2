package ua.songtotext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Малює текст. 2D: плоский по центру екрана. 3D: точка якоряться у світі перед гравцем і проєктується
 * на екран вручну (перспектива, масштаб за глибиною, звуження за кутом) — без шейдерів і частинок.
 */
public class HudRenderer {
    private static final long FADE_IN = 150, FADE_OUT = 400;
    private final Random rnd = new Random();

    private static class Shown {
        LyricsManager.Cue cue;
        long born, died = -1;
        double x, y, z;       // якір у світі
        double nx, nz;        // нормаль тексту (до гравця на момент появи)
        float tilt;           // нахил, рад
    }

    private final List<Shown> shown = new ArrayList<>();
    private int lastId = -1;
    private String lastKey = "";

    public void render(DrawContext ctx, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config cfg = SongToTextClient.config;
        PlayerEntity p = mc.player;
        MediaState st = SongToTextClient.POLLER.state();
        if (p == null || mc.options.hudHidden || !cfg.enabled || !st.hasTrack()
                || (cfg.hideOnPause && !st.playing())) { shown.clear(); return; }

        long now = System.currentTimeMillis();
        double sec = SongToTextClient.CLOCK.seconds(st, cfg.syncOffset);
        LyricsManager.Cue cue = SongToTextClient.LYRICS.cueAt(sec, cfg.display);

        if (!st.key().equals(lastKey)) { lastKey = st.key(); lastId = -1; shown.clear(); }
        int curId = cue == null ? -1 : cue.id();
        if (curId != lastId) {
            for (Shown s : shown) if (s.died < 0) s.died = now;
            if (cue != null) shown.add(spawn(p, cue, now, tc.getTickProgress(false)));
            lastId = curId;
        }

        Iterator<Shown> it = shown.iterator();
        while (it.hasNext()) {
            Shown s = it.next();
            if (s.died >= 0 && now - s.died > FADE_OUT) { it.remove(); continue; }
            float a = s.died >= 0 ? 1f - (now - s.died) / (float) FADE_OUT : Math.min(1f, (now - s.born) / (float) FADE_IN);
            float pop = 0.75f + 0.25f * (1f - (float) Math.pow(1 - Math.min(1f, (now - s.born) / (float) FADE_IN), 3));
            if (cfg.mode3d) draw3d(ctx, mc, cfg, s, a, pop, now, tc.getTickProgress(false));
            else draw2d(ctx, mc, cfg, s, a, pop);
        }
    }

    private Shown spawn(PlayerEntity p, LyricsManager.Cue cue, long now, float tp) {
        Shown s = new Shown();
        s.cue = cue; s.born = now;
        double yaw = Math.toRadians(p.getYaw(tp));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);       // вперед
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);      // вправо
        Vec3d eye = p.getCameraPosVec(tp);
        double side = (rnd.nextDouble() - 0.5) * 4.4, up = -0.3 + rnd.nextDouble() * 1.4;
        s.x = eye.x + fx * 5.5 + rx * side;
        s.z = eye.z + fz * 5.5 + rz * side;
        s.y = eye.y + up;
        s.nx = -fx; s.nz = -fz;
        s.tilt = (float) Math.toRadians((rnd.nextDouble() - 0.5) * 18);
        return s;
    }

    // ---------------- 2D ----------------
    private void draw2d(DrawContext ctx, MinecraftClient mc, Config cfg, Shown s, float alpha, float pop) {
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        TextRenderer tr = mc.textRenderer;
        boolean chunks = cfg.display == Config.DisplayMode.CHUNKS;
        float scale = (chunks ? 3.2f : 2.4f) * cfg.size / 100f * pop;
        int tw = tr.getWidth(styled(cfg, s.cue.text()));
        scale = Math.min(scale, w * 0.92f / Math.max(1, tw));
        drawText(ctx, tr, cfg, s.cue.text(), w / 2f, h * 0.30f, scale, 0f, 1f, alpha);
    }

    // ---------------- 3D ----------------
    private void draw3d(DrawContext ctx, MinecraftClient mc, Config cfg, Shown s, float alpha, float pop, long now, float tp) {
        PlayerEntity p = mc.player;
        int W = ctx.getScaledWindowWidth(), H = ctx.getScaledWindowHeight();
        double age = (now - s.born) / 1000.0;
        Vec3d eye = p.getCameraPosVec(tp);
        double yaw = Math.toRadians(p.getYaw(tp)), pitch = Math.toRadians(p.getPitch(tp));
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        double[] f = {-sy * cp, -sp, cy * cp};
        double[] r = {-cy, 0, -sy};
        double[] u = {r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]};

        double dx = s.x - eye.x, dy = s.y + age * 0.12 - eye.y, dz = s.z - eye.z;
        double zc = dx * f[0] + dy * f[1] + dz * f[2];
        if (zc < 0.3) return;
        double xc = dx * r[0] + dy * r[1] + dz * r[2];
        double yc = dx * u[0] + dy * u[1] + dz * u[2];

        double fov = Math.toRadians(mc.options.getFov().getValue());
        double focal = (H / 2.0) / Math.tan(fov / 2);
        float sx = (float) (W / 2.0 + xc / zc * focal), syy = (float) (H / 2.0 - yc / zc * focal);

        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double squash = Math.max(0.3, Math.abs((s.nx * -dx + s.nz * -dz) / Math.max(1e-4, Math.hypot(dx, dz))));
        TextRenderer tr = mc.textRenderer;
        float scale = (float) (0.075 * cfg.size / 100.0 * focal / zc) * pop;
        int tw = tr.getWidth(styled(cfg, s.cue.text()));
        scale = Math.min(scale, W * 0.9f / Math.max(1, tw));
        if (dist > 0) drawText(ctx, tr, cfg, s.cue.text(), sx, syy, scale, s.tilt, (float) squash, alpha);
    }

    // ---------------- текст ----------------
    private static Text styled(Config cfg, String t) {
        Style st = Style.EMPTY;
        if (cfg.font == Config.FontStyle.NEON) st = st.withBold(true).withItalic(true);
        return Text.literal(t).setStyle(st);
    }

    static int argb(float a, int rgb) {
        int al = MathHelper.clamp((int) (a * 255), 5, 255);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static int lighten(int rgb, float k) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        return ((int) (r + (255 - r) * k) << 16) | ((int) (g + (255 - g) * k) << 8) | (int) (b + (255 - b) * k);
    }

    private void drawText(DrawContext ctx, TextRenderer tr, Config cfg, String raw, float cx, float cy,
                          float scale, float rot, float xs, float alpha) {
        Text t = styled(cfg, raw);
        int w = tr.getWidth(t);
        int x = -w / 2, y = -4;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.rotate(rot);
        m.scale(scale * xs, scale);
        switch (cfg.font) {
            case NEON -> {                                   // жирний курсив з контуром, як TRYNA
                if (cfg.outline) {
                    for (int d = 2; d >= 1; d--)
                        for (int ox = -d; ox <= d; ox++) for (int oy = -d; oy <= d; oy++)
                            if (ox != 0 || oy != 0) ctx.drawText(tr, t, x + ox, y + oy, argb(alpha * (d == 2 ? 0.45f : 0.95f), cfg.color), false);
                }
                ctx.drawText(tr, t, x, y, argb(alpha, lighten(cfg.color, 0.8f)), false);
            }
            case PLAIN -> {                                  // тонкий білий
                if (cfg.outline) outline(ctx, tr, t, x, y, alpha * 0.7f, 0x000000);
                ctx.drawText(tr, t, x, y, argb(alpha, 0xFFFFFF), false);
            }
            default -> {                                     // Minecraft: колір з тінню
                if (cfg.outline) outline(ctx, tr, t, x, y, alpha, 0x000000);
                ctx.drawText(tr, t, x, y, argb(alpha, cfg.color), true);
            }
        }
        m.popMatrix();
    }

    private static void outline(DrawContext ctx, TextRenderer tr, Text t, int x, int y, float a, int rgb) {
        for (int ox = -1; ox <= 1; ox++) for (int oy = -1; oy <= 1; oy++)
            if (ox != 0 || oy != 0) ctx.drawText(tr, t, x + ox, y + oy, argb(a, rgb), false);
    }
}
