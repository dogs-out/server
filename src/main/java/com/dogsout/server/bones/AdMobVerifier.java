package com.dogsout.server.bones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks that a rewarded-ad callback really comes from Google.
 *
 * <p>AdMob signs the query string with one of its published ECDSA keys; the signed
 * part is everything before {@code &signature=}. Without this check anyone could
 * call the callback URL and collect bones.
 * See https://developers.google.com/admob/android/ssv
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdMobVerifier {

    static final String KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";
    private static final Duration KEYS_TTL = Duration.ofHours(12);

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<Long, PublicKey> keys = new ConcurrentHashMap<>();
    private volatile Instant keysFetchedAt = Instant.EPOCH;

    /** True when rawQuery carries a valid Google signature. */
    public boolean verify(String rawQuery) {
        if (rawQuery == null) return false;
        int sigAt = rawQuery.indexOf("&signature=");
        if (sigAt < 0) return false;
        String signed = rawQuery.substring(0, sigAt);
        Map<String, String> tail = parse(rawQuery.substring(sigAt + 1));
        String signature = tail.get("signature");
        String keyId = tail.get("key_id");
        if (signature == null || keyId == null) return false;
        try {
            PublicKey key = key(Long.parseLong(keyId));
            if (key == null) return false;
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(key);
            verifier.update(signed.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getUrlDecoder().decode(signature));
        } catch (Exception e) {
            log.warn("AdMob signature check failed: {}", e.getMessage());
            return false;
        }
    }

    /** Query parameters, URL-decoded. */
    public static Map<String, String> parse(String rawQuery) {
        Map<String, String> out = new java.util.HashMap<>();
        if (rawQuery == null) return out;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            out.put(pair.substring(0, eq),
                    java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    /** For tests, and for a key Google has not published yet. */
    void putKey(long keyId, PublicKey key) {
        keys.put(keyId, key);
        keysFetchedAt = Instant.now();
    }

    private PublicKey key(long keyId) throws Exception {
        PublicKey known = keys.get(keyId);
        // A key id we do not know yet may simply be newer than our copy of the list.
        if (known != null && keysFetchedAt.plus(KEYS_TTL).isAfter(Instant.now())) return known;
        refreshKeys();
        return keys.get(keyId);
    }

    private synchronized void refreshKeys() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(KEYS_URL)).timeout(Duration.ofSeconds(5)).GET().build();
        JsonNode json = objectMapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
        KeyFactory factory = KeyFactory.getInstance("EC");
        for (JsonNode k : json.path("keys")) {
            byte[] der = Base64.getDecoder().decode(k.path("base64").asText());
            keys.put(k.path("keyId").asLong(), factory.generatePublic(new X509EncodedKeySpec(der)));
        }
        keysFetchedAt = Instant.now();
    }
}
