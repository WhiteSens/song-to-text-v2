package ua.songtotext;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SongToTextClient implements ClientModInitializer {
    public static final String MOD_ID = "songtotext";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static Config config;
    public static final MediaPoller POLLER = new MediaPoller();
    public static final LyricsManager LYRICS = new LyricsManager();
    public static final SyncClock CLOCK = new SyncClock();
    private static final HudRenderer HUD = new HudRenderer();

    private static KeyBinding menuKey, toggleKey;
    private static MediaState sticky = MediaState.EMPTY;
    private static long stickyAt = 0;

    /** Стан без миготіння: якщо Windows на мить віддала порожні метадані (перемотування), тримаємо попередні до 4 с. */
    public static MediaState current() {
        MediaState s = POLLER.state();
        long now = System.currentTimeMillis();
        if (s.hasTrack()) { sticky = s; stickyAt = now; return s; }
        return now - stickyAt < 4000 ? sticky : s;
    }

    @Override
    public void onInitializeClient() {
        config = Config.load();

        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.songtotext.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, cat));
        toggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.songtotext.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, cat));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.wasPressed()) mc.setScreen(new SongToTextScreen(mc.currentScreen));
            while (toggleKey.wasPressed()) {            // Y: тільки вмикає/вимикає текст, меню не відкривається
                config.enabled = !config.enabled;
                config.save();
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal("Song to text: " + (config.enabled ? "ON" : "OFF")), true);
                }
            }
            MediaState s = current();
            CLOCK.tick(s);
            if (s.hasTrack() && !s.key().equals(LYRICS.loadedKey())) LYRICS.request(s);
        });

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "lyrics"), HUD::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> POLLER.stop());
        POLLER.start();
    }

    public static MinecraftClient mc() { return MinecraftClient.getInstance(); }
}
