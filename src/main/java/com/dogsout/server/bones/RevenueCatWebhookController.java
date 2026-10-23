package com.dogsout.server.bones;

import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Purchases, as RevenueCat reports them. The app never tells the server what was
 * bought; it only buys, and RevenueCat — having checked the receipt with Apple or
 * Google — calls here. The app user id in RevenueCat is our user id.
 *
 * <p>Set up in RevenueCat → Integrations → Webhooks: URL
 * https://api.dogsout.app/revenuecat/webhook, and the Authorization header value
 * that is also in REVENUECAT_WEBHOOK_AUTH on the server.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class RevenueCatWebhookController {

    static final String PREMIUM_ENTITLEMENT = "premium";
    static final Map<String, Integer> BONE_PACKS = Map.of("bones_10", 10, "bones_30", 30);

    /** Events after which Premium runs until the event's expiration. */
    private static final Set<String> PREMIUM_UNTIL_EXPIRATION = Set.of(
            "INITIAL_PURCHASE", "RENEWAL", "PRODUCT_CHANGE", "UNCANCELLATION",
            // A cancellation keeps Premium to the end of the paid period; so does an
            // expiration, whose expiration time is simply in the past.
            "CANCELLATION", "EXPIRATION", "BILLING_ISSUE", "SUBSCRIPTION_EXTENDED",
            "TEMPORARY_ENTITLEMENT_GRANT");

    private final UserRepository userRepository;
    private final RevenueCatEventRepository events;
    private final BoneService boneService;

    @Value("${REVENUECAT_WEBHOOK_AUTH:}")
    private String expectedAuth;

    @PostMapping("/revenuecat/webhook")
    @Transactional
    public ResponseEntity<Void> webhook(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @RequestBody JsonNode body) {
        if (expectedAuth.isBlank() || auth == null || !constantTimeEquals(auth, expectedAuth)) {
            return ResponseEntity.status(401).build();
        }
        JsonNode event = body.path("event");
        String id = event.path("id").asText(null);
        String type = event.path("type").asText("");
        if (id == null) return ResponseEntity.ok().build();
        if (events.existsById(id)) return ResponseEntity.ok().build();

        User user = userOf(event);
        String productId = event.path("product_id").asText(null);
        if (user != null) apply(user, type, productId, event);
        else log.warn("RevenueCat {} for an unknown app user {}", type, event.path("app_user_id").asText());

        RevenueCatEvent seen = new RevenueCatEvent();
        seen.setEventId(id);
        seen.setUserId(user != null ? user.getId() : null);
        seen.setType(type);
        seen.setProductId(productId);
        seen.setReceivedAt(Instant.now());
        events.save(seen);
        return ResponseEntity.ok().build();
    }

    private void apply(User user, String type, String productId, JsonNode event) {
        boolean premiumEvent = false;
        for (JsonNode e : event.path("entitlement_ids")) {
            if (PREMIUM_ENTITLEMENT.equals(e.asText())) premiumEvent = true;
        }
        if (premiumEvent && PREMIUM_UNTIL_EXPIRATION.contains(type)) {
            long expires = event.path("expiration_at_ms").asLong(0);
            user.setPremiumUntil(expires > 0 ? Instant.ofEpochMilli(expires) : null);
            userRepository.save(user);
            return;
        }
        // A bone pack is a consumable: it arrives as a one-off purchase.
        if ("NON_RENEWING_PURCHASE".equals(type) && productId != null && BONE_PACKS.containsKey(productId)) {
            boneService.grant(user, BONE_PACKS.get(productId));
        }
    }

    /** The app logs in to RevenueCat with our user id; earlier anonymous ids are aliases. */
    private User userOf(JsonNode event) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        ids.add(event.path("app_user_id").asText(""));
        event.path("aliases").forEach(a -> ids.add(a.asText()));
        for (String candidate : ids) {
            try {
                var found = userRepository.findById(Long.valueOf(candidate));
                if (found.isPresent()) return found.get();
            } catch (NumberFormatException ignored) {
                // An anonymous RevenueCat id ($RCAnonymousID:…), not ours.
            }
        }
        return null;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
