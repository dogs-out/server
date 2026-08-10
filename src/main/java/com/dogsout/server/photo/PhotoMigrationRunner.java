package com.dogsout.server.photo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-shot backfill of the photos that predate object storage, when they were held
 * inline in {@code user_photos.image_data} / {@code dog_photos.image_data} as
 * base64 data URIs.
 *
 * <p>Runs only with {@code photo.migrate=true}, so it is enabled for one deploy and
 * then turned off again. It is <b>idempotent</b>: a row whose {@code storage_key} is
 * already set is skipped, so an interrupted run resumes cleanly and a second run is
 * a no-op.
 *
 * <p>It deliberately goes through {@link PhotoService}, not a separate resize script,
 * so migrated photos come out byte-identical to what a fresh upload produces — same
 * renditions, same quality, same key layout.
 *
 * <p>The legacy columns are <em>not</em> dropped here. {@code ddl-auto=update} never
 * drops columns, and keeping the base64 until the migration has been eyeballed in the
 * app is the only rollback that exists. Drop them by hand afterwards:
 * <pre>
 * ALTER TABLE user_photos DROP COLUMN image_data;
 * ALTER TABLE dog_photos  DROP COLUMN image_data;
 * ALTER TABLE users       DROP COLUMN profile_picture;
 * ALTER TABLE dogs        DROP COLUMN profile_picture;
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "photo.migrate", havingValue = "true")
public class PhotoMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final PhotoService photoService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("photo.migrate=true — backfilling inline base64 photos into object storage");
        relaxLegacyConstraints();
        int users = migrateTable("user_photos", PhotoService.OWNER_USER);
        int dogs = migrateTable("dog_photos", PhotoService.OWNER_DOG);
        int userCovers = backfillCoverKeys("users", "user_photos", "user_id");
        int dogCovers = backfillCoverKeys("dogs", "dog_photos", "dog_id");
        log.info("Photo migration finished: {} user photos, {} dog photos, "
                + "{} user cover keys, {} dog cover keys", users, dogs, userCovers, dogCovers);
    }

    /**
     * Drops NOT NULL from the legacy {@code image_data} columns.
     *
     * <p>Without this the deploy breaks every new upload: the entities no longer map
     * {@code image_data}, so the INSERT omits it, and Postgres rejects the row against
     * a NOT NULL column with no default. Run it as a pre-deploy step too — this
     * safety net only fires once the app is already up.
     */
    private void relaxLegacyConstraints() {
        for (String table : List.of("user_photos", "dog_photos")) {
            if (hasLegacyColumn(table)) {
                jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN image_data DROP NOT NULL");
                log.info("{}.image_data is now nullable", table);
            }
        }
    }

    private int migrateTable(String table, String ownerType) {
        if (!hasLegacyColumn(table)) {
            log.info("{} has no image_data column — already migrated, nothing to do", table);
            return 0;
        }

        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM " + table + " WHERE image_data IS NOT NULL AND storage_key IS NULL ORDER BY id",
                Long.class);
        log.info("{}: {} row(s) to migrate", table, ids.size());

        int migrated = 0;
        for (Long id : ids) {
            // Fetched one at a time on purpose: these rows average ~800 kB of base64,
            // so selecting them all at once would pull tens of MB into heap.
            String imageData = jdbc.queryForObject(
                    "SELECT image_data FROM " + table + " WHERE id = ?", String.class, id);
            try {
                String key = photoService.storeDataUri(ownerType, imageData);
                jdbc.update("UPDATE " + table + " SET storage_key = ? WHERE id = ?", key, id);
                migrated++;
                log.info("{} {}/{}: stored as {}", table, migrated, ids.size(), key);
            } catch (RuntimeException e) {
                // One unreadable image must not strand the rest. The row keeps its
                // base64 and a null storage_key, so a later run retries it.
                log.error("{} id={} could not be migrated: {}", table, id, e.toString());
            }
        }
        return migrated;
    }

    /**
     * Points each owner's denormalized cover key at its {@code sort_order = 0} photo.
     * In production these were byte-identical copies of that photo, so this loses
     * nothing and removes the possibility of the two drifting apart.
     */
    private int backfillCoverKeys(String ownerTable, String photoTable, String ownerColumn) {
        return jdbc.update(
                "UPDATE " + ownerTable + " o SET profile_picture_key = p.storage_key "
                        + "FROM " + photoTable + " p "
                        + "WHERE p." + ownerColumn + " = o.id AND p.sort_order = 0 "
                        + "AND p.storage_key IS NOT NULL AND o.profile_picture_key IS NULL");
    }

    private boolean hasLegacyColumn(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_name = ? AND column_name = 'image_data'",
                Integer.class, table);
        return count != null && count > 0;
    }
}
