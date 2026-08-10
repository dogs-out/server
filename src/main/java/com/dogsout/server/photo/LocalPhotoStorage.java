package com.dogsout.server.photo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Stores renditions as files under {@code upload.dir}, served back by the
 * existing {@code /uploads/**} static resource handler.
 *
 * <p>Fine for local development, and a legitimate production setup on a mounted
 * persistent volume. Note the two properties a volume does not give you that a
 * bucket does: the files are not covered by the database's backups, and they pin
 * the app to a single instance.
 */
@Slf4j
public class LocalPhotoStorage implements PhotoStorage {

    private final Path root;
    private final String baseUrl;

    public LocalPhotoStorage(String uploadDir, String baseUrl) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public void store(String key, Map<PhotoRendition, byte[]> renditions) {
        Path dir = resolveWithin(key);
        try {
            Files.createDirectories(dir);
            for (Map.Entry<PhotoRendition, byte[]> entry : renditions.entrySet()) {
                Files.write(dir.resolve(entry.getKey().filename()), entry.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write photo " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        Path dir = resolveWithin(key);
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    // A leftover file wastes disk but must not fail the user's delete.
                    log.warn("Could not delete {}: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Could not walk {} for deletion: {}", dir, e.getMessage());
        }
    }

    @Override
    public String url(String key, PhotoRendition rendition) {
        return baseUrl + "/uploads/" + key + "/" + rendition.filename();
    }

    /**
     * Resolves a key under the upload root, refusing anything that escapes it.
     * Keys are server-generated today, but this class writes to the filesystem —
     * the containment check is what keeps a future caller-influenced key from
     * turning into the arbitrary file write that {@code UploadController} had.
     */
    private Path resolveWithin(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid photo key");
        }
        return target;
    }
}
