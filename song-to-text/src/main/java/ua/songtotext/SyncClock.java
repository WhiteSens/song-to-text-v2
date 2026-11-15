package ua.songtotext;

/**
 * Позиція пісні в секундах.
 * Якщо медіапанель віддає позицію, що рухається (Spotify, YouTube Music, інколи й SoundCloud) — беремо її.
 * Інакше секундомір від зміни треку (зупиняється на паузі) + ручний зсув.
 */
public class SyncClock {
    private String key = "";
    private double stopwatchMs = 0;
    private long lastTick = System.currentTimeMillis();
    private long lastRecv = -1, lastPos = -1;
    private boolean advancing = false;

    public void tick(MediaState s) {
        long now = System.currentTimeMillis();
        if (!s.key().equals(key)) { key = s.key(); stopwatchMs = 0; advancing = false; lastRecv = -1; lastPos = -1; }
        else if (s.playing()) stopwatchMs += now - lastTick;
        lastTick = now;
        if (s.receivedAt() != lastRecv) {
            if (s.playing() && s.endMs() > 0 && lastPos >= 0 && s.posMs() != lastPos) advancing = true;
            lastRecv = s.receivedAt(); lastPos = s.posMs();
        }
    }

    private boolean useTimeline(MediaState s) {
        return s.endMs() > 0 && (advancing || !s.isSoundCloud());
    }

    public double seconds(MediaState s, double offsetSec) {
        double ms = useTimeline(s)
                ? s.posMs() + (s.playing() ? System.currentTimeMillis() - s.receivedAt() : 0)
                : stopwatchMs;
        return ms / 1000.0 + offsetSec;
    }
}
