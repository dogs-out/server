package com.dogsout.server.upload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the path-traversal hole in image upload: the extension used
 * to be sliced off the client-supplied Content-Type, so a crafted media type could
 * escape the upload directory via Path.resolve.
 */
class UploadControllerTest {

    private UploadController controllerFor(Path dir) {
        UploadController controller = new UploadController();
        ReflectionTestUtils.setField(controller, "uploadDir", dir.toString());
        ReflectionTestUtils.setField(controller, "baseUrl", "https://example.test");
        return controller;
    }

    @Test
    void acceptsAKnownImageTypeAndStoresItInsideTheUploadDir(@TempDir Path dir) throws IOException {
        ResponseEntity<Map<String, String>> response = controllerFor(dir).uploadImage(
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", "not-really-a-jpeg".getBytes()));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String url = response.getBody().get("url");
        assertThat(url).startsWith("https://example.test/uploads/").endsWith(".jpg");

        try (var files = Files.list(dir)) {
            assertThat(files.count()).isEqualTo(1);
        }
    }

    @Test
    void rejectsATraversalCraftedIntoTheContentType(@TempDir Path root) throws IOException {
        Path dir = root.resolve("uploads");
        Path escapeTarget = root.resolve("pwned.txt");

        ResponseEntity<Map<String, String>> response = controllerFor(dir).uploadImage(
                new MockMultipartFile("file", "photo.jpg",
                        "image/../../pwned.txt", "payload".getBytes()));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        // The whole point: nothing may be written outside the upload directory
        assertThat(Files.exists(escapeTarget)).isFalse();
    }

    @Test
    void rejectsNonImageAndMissingContentTypes(@TempDir Path dir) throws IOException {
        UploadController controller = controllerFor(dir);

        assertThat(controller.uploadImage(new MockMultipartFile(
                "file", "x.pdf", "application/pdf", "x".getBytes()))
                .getStatusCode().value()).isEqualTo(400);

        assertThat(controller.uploadImage(new MockMultipartFile(
                "file", "x", null, "x".getBytes()))
                .getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void toleratesContentTypeParameters(@TempDir Path dir) throws IOException {
        ResponseEntity<Map<String, String>> response = controllerFor(dir).uploadImage(
                new MockMultipartFile("file", "photo.png", "image/PNG; charset=binary", "x".getBytes()));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().get("url")).endsWith(".png");
    }
}
