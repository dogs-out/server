package com.dogsout.server.photo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

/**
 * The one place that knows how a photo becomes bytes in storage: it mints the
 * key, runs the {@link ImageProcessor}, and hands the renditions to whichever
 * {@link PhotoStorage} is configured.
 *
 * <p>Keys are {@code photos/<owner>/<uuid>} — random, so a key reveals nothing
 * about who owns the photo or how many exist, and a re-upload never collides
 * with the object it replaces (which is what lets photos be cached forever).
 */
@Service
@RequiredArgsConstructor
public class PhotoService {

    public static final String OWNER_USER = "user";
    public static final String OWNER_DOG = "dog";

    private final ImageProcessor imageProcessor;
    private final PhotoStorage storage;

    /** Stores an uploaded file and returns its storage key. */
    public String store(String ownerType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No image supplied");
        }
        try {
            return store(ownerType, file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read upload", e);
        }
    }

    /** Stores raw image bytes and returns the storage key. */
    public String store(String ownerType, byte[] bytes) {
        String key = "photos/" + ownerType + "/" + UUID.randomUUID();
        storage.store(key, imageProcessor.process(bytes));
        return key;
    }

    /**
     * Stores a {@code data:image/…;base64,…} URI. Only used by the migration of the
     * legacy inline-base64 rows; new uploads arrive as multipart.
     */
    public String storeDataUri(String ownerType, String dataUri) {
        return store(ownerType, decodeDataUri(dataUri));
    }

    public void delete(String key) {
        if (key != null && !key.isBlank()) {
            storage.delete(key);
        }
    }

    public String url(String key, PhotoRendition rendition) {
        return key == null || key.isBlank() ? null : storage.url(key, rendition);
    }

    static byte[] decodeDataUri(String dataUri) {
        if (dataUri == null) {
            throw new IllegalArgumentException("Image is null");
        }
        int comma = dataUri.indexOf(',');
        // Tolerate a bare base64 payload as well as a full data: URI — both shapes
        // exist in the legacy rows depending on which client version wrote them.
        String payload = dataUri.startsWith("data:") && comma >= 0
                ? dataUri.substring(comma + 1)
                : dataUri;
        return Base64.getDecoder().decode(payload);
    }
}
