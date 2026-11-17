package ua.songtotext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Кеш текстур з рядками пісні (малює TextRaster). Лише 48 останніх, старі видаляються. */
public final class LyricTextures {
    public record Tex(Identifier id, int w, int h) { }

    private static int counter = 0;
    private static final LinkedHashMap<String, Tex> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Tex> e) {
            if (size() > 48) { MinecraftClient.getInstance().getTextureManager().destroyTexture(e.getValue().id()); return true; }
            return false;
        }
    };

    private LyricTextures() { }

    public static Tex get(String text, int fi, int rgb, boolean outline, int px) {
        String key = fi + "|" + rgb + "|" + outline + "|" + px + "|" + text;
        Tex t = CACHE.get(key);
        if (t != null) return t;
        BufferedImage bi = TextRaster.render(text, fi, rgb, outline, px);
        int w = bi.getWidth(), h = bi.getHeight();
        NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "songtotext_lyric", new NativeImage(NativeImage.Format.RGBA, w, h, false));
        int[] px2 = bi.getRGB(0, 0, w, h, null, 0, w);
        NativeImage img = tex.getImage();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setColorArgb(x, y, px2[y * w + x]);
        tex.upload();
        Identifier id = Identifier.of(SongToTextClient.MOD_ID, "lyric/" + (counter++));
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
        t = new Tex(id, w, h);
        CACHE.put(key, t);
        return t;
    }
}
