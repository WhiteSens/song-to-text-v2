package ua.songtotext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
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
 * Шрифт Minecraft малюється самою грою, решта шрифтів — Java2D-текстурами (TextRaster/LyricTextures).
 */
public class HudRenderer {
    private static final long FADE_OUT = 400;
    private final Random rnd = new Random();

    private static class Shown {
        LyricsManager.Cue cue;
        long born, died = -1, offSince = 0;
        double x, y, z;       // якір у світі
        double nx, nz;        // нормаль тексту (до гравця на момент появи)
        double fx, fz, dist;  // напрям «вперед» і відстань на момент появи
        float tilt;           // нахил, рад
    }

    /** Стан анімації появи: прозорість, множник масштабу, зсув по вертикалі (у висотах рядка), частка показаних символів. */
    private record Anim(float alpha, float scale, float dy, float reveal) { }

    private final List<Shown> shown = new ArrayList<>();
    private int lastId = -1;
    private String lastKey = "";

    private static float easeOut(float t) { t = Math.max(0f, Math.min(1f, t)); return 1f - (float) Math.pow(1 - t, 3); }

    private static Anim anim(Config.AnimStyle st, long age, boolean dying, int chars) {
        if (dying) return new Anim(1f, 1f, 0f, 1f);
        switch (st) {
            case SIMPLE: return new Anim(Math.min(1f, age / 220f), 1f, 0f, 1f);
            case SLIDE:  return new Anim(Math.min(1f, age / 220f), 1f, (1f - easeOut(age / 300f)) * 0.9f, 1f);
            case ZOOM:   return new Anim(Math.min(1f, age / 240f), 1f + 0.8f * (1f - easeOut(age / 320f)), 0f, 1f);
            case TYPE: {
                float dur = Math.max(200, Math.min(700, chars * 30));
                return new Anim(Math.min(1f, age / 90f), 1f, 0f, Math.min(1f, age / dur));
            }
            default:     return new Anim(Math.min(1f, age / 150f), 0.75f + 0.25f * easeOut(age / 160f), 0f, 1f);   // POP
        }
    }

    public void render(DrawContext ctx, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config cfg = SongToTextClient.config;
        PlayerEntity p = mc.player;
        MediaState st = SongToTextClient.current();
        if (p == null || mc.options.hudHidden || !cfg.enabled || !st.hasTrack()
                || (cfg.hideOnPause && !SongToTextClient.CLOCK.playing(st))) { shown.clear(); return; }

        long now = System.currentTimeMillis();
        double sec = SongToTextClient.CLOCK.seconds(st, cfg.syncOffset);
        LyricsManager.Cue cue = SongToTextClient.LYRICS.cueAt(sec, cfg.display);
        float tp = tc.getTickProgress(false);

        if (!st.key().equals(lastKey)) { lastKey = st.key(); lastId = -1; shown.clear(); }
        int curId = cue == null ? -1 : cue.id();
        if (curId != lastId) {
            for (Shown s : shown) if (s.died < 0) s.died = now;
            if (cue != null) shown.add(spawn(p, cue, now, tp));
            lastId = curId;
        }

        Iterator<Shown> it = shown.iterator();
        while (it.hasNext()) {
            Shown s = it.next();
            if (s.died >= 0 && now - s.died > FADE_OUT) { it.remove(); continue; }
            float aOut = s.died >= 0 ? 1f - (now - s.died) / (float) FADE_OUT : 1f;
            Anim an = anim(cfg.anim, now - s.born, s.died >= 0, s.cue.text().length());
            float alpha = aOut * an.alpha();
            if (cfg.mode3d) draw3d(ctx, mc, cfg, s, alpha, an, now, tp);
            else draw2d(ctx, mc, cfg, s, alpha, an);
        }
    }

    private Shown spawn(PlayerEntity p, LyricsManager.Cue cue, long now, float tp) {
        Shown s = new Shown();
        s.cue = cue; s.born = now;
        place(s, p, tp);
        return s;
    }

    /** Ставить якір перед гравцем у напрямку, куди він зараз дивиться. */
    private void place(Shown s, PlayerEntity p, float tp) {
        double yaw = Math.toRadians(p.getYaw(tp));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);       // вперед
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);      // вправо
        Vec3d eye = p.getCameraPosVec(tp);
        double dist = SongToTextClient.config.distance;      // 1..100 блоків
        double k = dist / 5.5;                               // розкид масштабується, щоб кутове положення лишалось тим самим
        double side = (rnd.nextDouble() - 0.5) * 4.4 * k, up = (-0.3 + rnd.nextDouble() * 1.4) * k;
        s.x = eye.x + fx * dist + rx * side;
        s.z = eye.z + fz * dist + rz * side;
        s.y = eye.y + up;
        s.fx = fx; s.fz = fz; s.dist = dist;
        s.nx = -fx; s.nz = -fz;
        s.tilt = (float) Math.toRadians((rnd.nextDouble() - 0.5) * 18);
    }

    // ---------------- 2D ----------------
    private void draw2d(DrawContext ctx, MinecraftClient mc, Config cfg, Shown s, float alpha, Anim an) {
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        boolean chunks = cfg.display == Config.DisplayMode.CHUNKS;
        float scale = (chunks ? 3.2f : 2.4f) * cfg.size / 100f * an.scale();
        drawText(ctx, mc, cfg, s.cue.text(), w / 2f, h * 0.30f, scale, 0f, 1f, alpha, an, w * 0.92f);
    }

    // ---------------- 3D ----------------
    private void draw3d(DrawContext ctx, MinecraftClient mc, Config cfg, Shown s, float alpha, Anim an, long now, float tp) {
        PlayerEntity p = mc.player;
        int W = ctx.getScaledWindowWidth(), H = ctx.getScaledWindowHeight();
        double age = (now - s.born) / 1000.0;
        Vec3d eye = p.getCameraPosVec(tp);
        double yaw = Math.toRadians(p.getYaw(tp)), pitch = Math.toRadians(p.getPitch(tp));
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        double[] f = {-sy * cp, -sp, cy * cp};
        double[] r = {-cy, 0, -sy};
        double[] u = {r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]};

        // якщо гравець добіг до тексту — штовхаємо якір уперед, щоб його не можна було перебігти
        double ahead = (s.x - eye.x) * s.fx + (s.z - eye.z) * s.fz;
        double minAhead = Math.max(0.8, s.dist * 0.4);
        if (ahead < minAhead) { s.x += s.fx * (minAhead - ahead); s.z += s.fz * (minAhead - ahead); }

        double dx = s.x - eye.x, dy = s.y + age * 0.12 - eye.y, dz = s.z - eye.z;

        // гравець розвернувся (напр. на 180°) — текст лишився позаду: ставимо його перед очима
        double hl = Math.hypot(dx, dz);
        double cosH = hl < 1e-4 ? 1.0 : (-sy * dx + cy * dz) / hl;       // кут між поглядом і напрямком на текст (по горизонталі)
        if (cosH < 0.3 && s.died < 0) {
            if (s.offSince == 0) s.offSince = now;
            if (now - s.offSince > 250) { place(s, p, tp); s.born = now; s.offSince = 0; return; }
        } else {
            s.offSince = 0;
        }

        double zc = dx * f[0] + dy * f[1] + dz * f[2];
        if (zc < 0.3) return;
        double xc = dx * r[0] + dy * r[1] + dz * r[2];
        double yc = dx * u[0] + dy * u[1] + dz * u[2];

        double fov = Math.toRadians(mc.options.getFov().getValue());
        double focal = (H / 2.0) / Math.tan(fov / 2);
        float sx = (float) (W / 2.0 + xc / zc * focal), syy = (float) (H / 2.0 - yc / zc * focal);

        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double squash = Math.max(0.3, Math.abs((s.nx * -dx + s.nz * -dz) / Math.max(1e-4, Math.hypot(dx, dz))));
        float scale = (float) (0.075 * Math.sqrt(s.dist / 5.5) * cfg.size / 100.0 * focal / zc) * an.scale();   // sqrt: далекий текст росте, щоб лишатись читабельним
        if (dist > 0) drawText(ctx, mc, cfg, s.cue.text(), sx, syy, scale, s.tilt, (float) squash, alpha, an, W * 0.92f);
    }

    // ---------------- текст ----------------
    static int argb(float a, int rgb) {
        int al = MathHelper.clamp((int) (a * 255), 5, 255);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private void drawText(DrawContext ctx, MinecraftClient mc, Config cfg, String raw, float cx, float cy,
                          float scale, float rot, float xs, float alpha, Anim an, float maxW) {
        cy += an.dy() * 9f * scale;
        if (cfg.font == Config.FontStyle.MINECRAFT) drawMc(ctx, mc.textRenderer, cfg, raw, cx, cy, scale, rot, xs, alpha, an, maxW);
        else drawRaster(ctx, mc, cfg, raw, cx, cy, scale, rot, xs, alpha, an, maxW);
    }

    /** Шрифт самого Minecraft. */
    private void drawMc(DrawContext ctx, TextRenderer tr, Config cfg, String raw, float cx, float cy,
                        float scale, float rot, float xs, float alpha, Anim an, float maxW) {
        int tw = tr.getWidth(Text.literal(raw));
        float sc = Math.min(scale, maxW / Math.max(1, tw));
        int shownChars = Math.max(0, Math.min(raw.length(), (int) Math.ceil(raw.length() * an.reveal())));
        Text part = Text.literal(raw.substring(0, shownChars));
        int x = -tw / 2, y = -4;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.rotate(rot);
        m.scale(sc * xs, sc);
        if (cfg.outline) {
            for (int ox = -1; ox <= 1; ox++) for (int oy = -1; oy <= 1; oy++)
                if (ox != 0 || oy != 0) ctx.drawText(tr, part, x + ox, y + oy, argb(alpha, 0x000000), false);
        }
        ctx.drawText(tr, part, x, y, argb(alpha, cfg.color), true);
        m.popMatrix();
    }

    /** Інші шрифти: рядок малюється Java2D у текстуру потрібного розміру й показується 1:1 до пікселів екрана. */
    private void drawRaster(DrawContext ctx, MinecraftClient mc, Config cfg, String raw, float cx, float cy,
                            float scale, float rot, float xs, float alpha, Anim an, float maxW) {
        double sf = mc.getWindow().getScaleFactor();
        float targetPx = 9f * scale * (float) sf * 1.15f;           // потрібний розмір шрифту в пікселях екрана
        if (targetPx < 4f) return;
        int bucket = MathHelper.clamp(Math.round(targetPx / 6f) * 6, 12, 240);   // квантуємо, щоб не перемальовувати щокадру
        LyricTextures.Tex t = LyricTextures.get(raw, cfg.font.ordinal(), cfg.color, cfg.outline, bucket);
        float k = targetPx / bucket / (float) sf;                    // gui-одиниць на піксель текстури
        if (t.w() * k > maxW) k = maxW / t.w();
        int rw = Math.max(1, Math.min(t.w(), (int) (t.w() * an.reveal())));     // «друкарська машинка»: обрізаємо праворуч
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.rotate(rot);
        m.scale(k * xs, k);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, t.id(), -t.w() / 2, -t.h() / 2, 0f, 0f, rw, t.h(), rw, t.h(), t.w(), t.h(),
                argb(alpha, 0xFFFFFF));
        m.popMatrix();
    }
}
