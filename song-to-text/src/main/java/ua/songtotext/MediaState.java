package ua.songtotext;

/** Знімок медіапанелі. receivedAt — момент (ms), якому відповідає posMs. */
public record MediaState(String app, String title, String artist, boolean playing,
                         long posMs, long endMs, long receivedAt) {
    public static final MediaState EMPTY = new MediaState("", "", "", false, 0, 0, 0);

    public boolean hasTrack() { return !title.isBlank(); }
    public String key() { return app + "|" + artist + "|" + title; }
    public boolean isSoundCloud() { return app.toLowerCase().contains("soundcloud"); }
    /** Чи віддає джерело реальну позицію. SoundCloud — ні, тоді секундомір від зміни треку. */
    public boolean hasTimeline() { return !isSoundCloud() && endMs > 0; }
}
