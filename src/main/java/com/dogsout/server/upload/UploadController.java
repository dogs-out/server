package com.dogsout.server.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/upload")
public class UploadController {

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Extensions are looked up from this fixed table rather than derived from the
     * request. The client controls the multipart Content-Type, so deriving the
     * extension from it let a caller send {@code image/../../../etc/cron.d/evil} —
     * which passes a {@code startsWith("image/")} check and escapes the upload
     * directory through {@code Path.resolve}, giving arbitrary file write.
     */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/jpg",  "jpg",
            "image/png",  "png",
            "image/gif",  "gif",
            "image/webp", "webp",
            "image/heic", "heic",
            "image/heif", "heif"
    );

    @PostMapping("/image")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        // Strip any parameters ("image/jpeg; charset=…") before matching
        String mediaType = contentType == null ? "" :
                contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);

        String extension = ALLOWED_TYPES.get(mediaType);
        if (extension == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed"));
        }

        // Filename is now entirely server-generated: random UUID + whitelisted extension.
        String filename = UUID.randomUUID() + "." + extension;

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path target = dir.resolve(filename).normalize();
        // Defence in depth: even with a server-generated name, never write outside dir.
        if (!target.startsWith(dir)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file name"));
        }
        Files.copy(file.getInputStream(), target);

        String url = baseUrl + "/uploads/" + filename;
        return ResponseEntity.ok(Map.of("url", url));
    }
}