package com.dogsout.server.photo;

/**
 * The sizes every uploaded photo is stored in.
 *
 * <p>Two renditions rather than one because the app shows photos at two wildly
 * different scales: full-bleed cards in Discover and the profile carousels, and
 * ~40pt avatars in chat rows, attendee lists and the blocked-user list. Sending
 * a feed-sized image to fill a 40pt circle is what made the old feed heavy.
 *
 * <p>The original upload is deliberately <em>not</em> kept. {@link #FEED} at
 * 1080px wide already exceeds the logical width of any phone screen the app
 * targets, so an original would double storage to serve pixels nobody sees. The
 * trade-off is that a future larger rendition can only be generated from FEED,
 * not from the untouched original — acceptable while 1080px is the ceiling.
 */
public enum PhotoRendition {

    /** Full-card display: fits inside 1080×1440, preserving aspect ratio. */
    FEED("feed", 1080, 1440, 0.82f),

    /** Avatars and list rows: fits inside 256×256. */
    THUMB("thumb", 256, 256, 0.75f);

    private final String key;
    private final int maxWidth;
    private final int maxHeight;
    private final float quality;

    PhotoRendition(String key, int maxWidth, int maxHeight, float quality) {
        this.key = key;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.quality = quality;
    }

    /** Filename stem used in the storage key, e.g. {@code …/feed.jpg}. */
    public String key() {
        return key;
    }

    public int maxWidth() {
        return maxWidth;
    }

    public int maxHeight() {
        return maxHeight;
    }

    public float quality() {
        return quality;
    }

    public String filename() {
        return key + ".jpg";
    }
}
