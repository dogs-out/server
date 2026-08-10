package com.dogsout.server.photo;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.List;
import java.util.Map;

/**
 * Stores renditions in an S3-compatible bucket — Cloudflare R2, AWS S3, Supabase
 * Storage all speak this API.
 *
 * <p>Objects are written without an ACL and served from {@code photo.public-base-url},
 * which is expected to be a public bucket endpoint or CDN domain in front of one.
 * Nothing here is a secret: a photo URL is handed to any client that can see the
 * profile, so the bucket is public-read by design and access control lives at the
 * API layer that decides which profiles you get back in the first place.
 */
@Slf4j
public class S3PhotoStorage implements PhotoStorage {

    private static final String CONTENT_TYPE = "image/jpeg";

    /**
     * Photos are immutable once written — a new upload gets a new key — so they can
     * be cached indefinitely. This is most of the reason the feed gets cheap on a
     * second visit.
     */
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client client;
    private final String bucket;
    private final String publicBaseUrl;

    public S3PhotoStorage(S3Client client, String bucket, String publicBaseUrl) {
        this.client = client;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @Override
    public void store(String key, Map<PhotoRendition, byte[]> renditions) {
        for (Map.Entry<PhotoRendition, byte[]> entry : renditions.entrySet()) {
            byte[] bytes = entry.getValue();
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey(key, entry.getKey()))
                            .contentType(CONTENT_TYPE)
                            .contentLength((long) bytes.length)
                            .cacheControl(CACHE_CONTROL)
                            .build(),
                    RequestBody.fromBytes(bytes));
        }
    }

    @Override
    public void delete(String key) {
        List<ObjectIdentifier> objects = java.util.Arrays.stream(PhotoRendition.values())
                .map(rendition -> ObjectIdentifier.builder().key(objectKey(key, rendition)).build())
                .toList();
        try {
            client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(objects).build())
                    .build());
        } catch (S3Exception e) {
            // An orphaned object costs a few cents a year; failing the user's delete
            // because the bucket was briefly unreachable costs more.
            log.warn("Could not delete photo {} from bucket {}: {}", key, bucket, e.getMessage());
        }
    }

    @Override
    public String url(String key, PhotoRendition rendition) {
        return publicBaseUrl + "/" + objectKey(key, rendition);
    }

    private String objectKey(String key, PhotoRendition rendition) {
        return key + "/" + rendition.filename();
    }
}
