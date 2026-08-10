package com.dogsout.server.photo;

import java.util.Map;

/**
 * Where photo bytes live.
 *
 * <p>Two implementations ship: {@link LocalPhotoStorage} (a directory on disk,
 * used for local development and viable in production on a mounted volume) and
 * {@link S3PhotoStorage} (any S3-compatible bucket — Cloudflare R2, AWS S3,
 * Supabase). Which one runs is a config value, so moving the bytes to a bucket
 * later never touches the photo code: point the config at the new backend and copy
 * the objects across with any bucket-sync tool (rclone, {@code aws s3 sync}) — the
 * keys are identical on both sides, so nothing in the database changes.
 *
 * <p>A photo is addressed by a <em>storage key</em> — an opaque prefix such as
 * {@code photos/user/9f3c…} — under which every {@link PhotoRendition} is stored
 * as {@code <key>/<rendition>.jpg}. Storing the prefix rather than one URL per
 * rendition keeps the database column small, and means a new rendition can be
 * added later without a schema migration.
 */
public interface PhotoStorage {

    /**
     * Writes every rendition under {@code key}. Overwrites if the key exists, so
     * re-running a migration is safe.
     */
    void store(String key, Map<PhotoRendition, byte[]> renditions);

    /** Removes every rendition under {@code key}. Must not fail if already gone. */
    void delete(String key);

    /** Publicly fetchable URL for one rendition of {@code key}. */
    String url(String key, PhotoRendition rendition);
}
