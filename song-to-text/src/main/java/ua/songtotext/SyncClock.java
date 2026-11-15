package ua.songtotext;

/**
 * Позиція пісні в секундах. Стійка до перемотування й тимчасових збоїв метаданих:
 *  - позиція з медіапанелі, якщо вона є й рухається; якщо таймлайн на мить зник — продовжуємо від останньої відомої;
 *  - інакше секундомір від зміни треку (SoundCloud-застосунок);
 *  - «пауза» вважається паузою лише якщо трек не грає понад 1.5 с (буферизація після перемотування не рахується);
 *  - якщо ключ треку на мить змінився й повернувся (до 5 с), секундомір не скидається.
 */
public class SyncClock {
    private static final long PAUSE_GRACE_MS = 1500;
    private String key = "";
    private double stopwatchMs = 0;
    private long lastTick = System.currentTimeMillis();
    private long lastRecv = -1, lastPos = -1;
    private boolean advancing = false;
    private long lastPlayingAt = 0;
    private long goodPos = -1, goodAt = 0;
    private String prevKey = "";
    private double prevStopwatch = 0;
    private long prevAt = 0;

    public boolean playing(MediaState s) {
        return s.playing() || System.currentTimeMillis() - lastPlayingAt < PAUSE_GRACE_MS;
    }

    public void tick(MediaState s) {
        long now = System.currentTimeMillis();
        if (s.playing()) lastPlayingAt = now;
        if (!s.key().equals(key)) {
            double oldSw = stopwatchMs; String oldKey = key;
            if (s.key().equals(prevKey) && now - prevAt < 5000) {
                stopwatchMs = prevStopwatch + (now - prevAt);   // метадані «блимнули» й повернулись
            } else {
                stopwatchMs = 0; advancing = false; goodPos = -1; lastRecv = -1; lastPos = -1;
            }
            prevKey = oldKey; prevStopwatch = oldSw; prevAt = now; key = s.key();
        } else if (playing(s)) {
            stopwatchMs += now - lastTick;
        }
        lastTick = now;

        if (s.endMs() > 0) { goodPos = s.posMs(); goodAt = s.receivedAt(); }
        if (s.receivedAt() != lastRecv) {
            if (s.playing() && s.endMs() > 0 && lastPos >= 0 && s.posMs() != lastPos) advancing = true;
            lastRecv = s.receivedAt(); lastPos = s.posMs();
        }
    }

    public double seconds(MediaState s, double offsetSec) {
        long now = System.currentTimeMillis();
        boolean pl = playing(s);
        double ms;
        if (s.endMs() > 0 && (advancing || !s.isSoundCloud())) {
            ms = s.posMs() + (pl ? now - s.receivedAt() : 0);
        } else if (goodPos >= 0 && !s.isSoundCloud()) {
            ms = goodPos + (pl ? now - goodAt : 0);              // таймлайн тимчасово зник
        } else {
            ms = stopwatchMs;
        }
        return ms / 1000.0 + offsetSec;
    }
}
