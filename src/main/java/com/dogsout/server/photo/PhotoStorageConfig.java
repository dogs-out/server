package com.dogsout.server.photo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Selects the {@link PhotoStorage} implementation from configuration.
 *
 * <p>{@code photo.storage=local} (the default) writes to {@code upload.dir};
 * {@code photo.storage=s3} writes to an S3-compatible bucket. Storage keys are the
 * same either way, so switching means copying the objects across and flipping this
 * flag — no database change and no code change.
 *
 * <p>For Cloudflare R2 the settings are:
 * <pre>
 * photo.storage=s3
 * photo.s3.endpoint=https://&lt;account-id&gt;.r2.cloudflarestorage.com
 * photo.s3.region=auto
 * photo.s3.bucket=dogsout-photos
 * photo.s3.access-key / photo.s3.secret-key   (R2 API token)
 * photo.public-base-url=https://&lt;public bucket or CDN domain&gt;
 * </pre>
 */
@Slf4j
@Configuration
public class PhotoStorageConfig {

    @Bean
    @ConditionalOnProperty(name = "photo.storage", havingValue = "local", matchIfMissing = true)
    public PhotoStorage localPhotoStorage(
            @Value("${upload.dir:uploads}") String uploadDir,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        log.info("Photo storage: local directory {}", uploadDir);
        return new LocalPhotoStorage(uploadDir, baseUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "photo.storage", havingValue = "s3")
    public PhotoStorage s3PhotoStorage(
            @Value("${photo.s3.bucket}") String bucket,
            @Value("${photo.s3.endpoint:}") String endpoint,
            @Value("${photo.s3.region:auto}") String region,
            @Value("${photo.s3.access-key}") String accessKey,
            @Value("${photo.s3.secret-key}") String secretKey,
            @Value("${photo.public-base-url}") String publicBaseUrl) {

        var builder = S3Client.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));

        if (!endpoint.isBlank()) {
            // Non-AWS backends (R2, MinIO, Supabase) need the endpoint overridden, and
            // path-style addressing because they do not serve bucket-name subdomains.
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }

        log.info("Photo storage: S3-compatible bucket {} served from {}", bucket, publicBaseUrl);
        return new S3PhotoStorage(builder.build(), bucket, publicBaseUrl);
    }
}
