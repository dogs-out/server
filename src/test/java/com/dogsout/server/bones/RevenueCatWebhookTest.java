package com.dogsout.server.bones;

import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** What a purchase does, as RevenueCat reports it. */
class RevenueCatWebhookTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RevenueCatEventRepository events = mock(RevenueCatEventRepository.class);
    private final BoneService bones = new BoneService(users, Clock.systemUTC());
    private final RevenueCatWebhookController controller = new RevenueCatWebhookController(users, events, bones);
    private final ObjectMapper json = new ObjectMapper();
    private final User lea = new User();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "expectedAuth", "Bearer s3cret");
        lea.setId(2L);
        when(users.findById(2L)).thenReturn(Optional.of(lea));
    }

    private JsonNode event(String id, String type, String product, String entitlement, Instant expires) {
        var e = json.createObjectNode();
        e.put("id", id).put("type", type).put("app_user_id", "2").put("product_id", product);
        if (entitlement != null) e.putArray("entitlement_ids").add(entitlement);
        if (expires != null) e.put("expiration_at_ms", expires.toEpochMilli());
        return json.createObjectNode().set("event", e);
    }

    private int post(String auth, JsonNode body) {
        return controller.webhook(auth, body).getStatusCode().value();
    }

    @Test
    void aSubscriptionMakesThemPremiumUntilItExpires() {
        Instant month = Instant.now().plus(Duration.ofDays(30));
        assertThat(post("Bearer s3cret", event("e1", "INITIAL_PURCHASE", "premium_monthly", "premium", month))).isEqualTo(200);
        assertThat(lea.getPremiumUntil()).isEqualTo(Instant.ofEpochMilli(month.toEpochMilli()));
        assertThat(bones.isPremium(lea)).isTrue();
    }

    @Test
    void anExpirationEndsPremium() {
        lea.setPremiumUntil(Instant.now().plus(Duration.ofDays(3)));
        post("Bearer s3cret", event("e2", "EXPIRATION", "premium_monthly", "premium", Instant.now().minusSeconds(60)));
        assertThat(bones.isPremium(lea)).isFalse();
    }

    @Test
    void aBonePackAddsItsBones() {
        post("Bearer s3cret", event("e3", "NON_RENEWING_PURCHASE", "bones_30", null, null));
        assertThat(lea.getBonusBones()).isEqualTo(30);
    }

    @Test
    void aRetriedEventPaysOnce() {
        when(events.existsById("e4")).thenReturn(true);
        post("Bearer s3cret", event("e4", "NON_RENEWING_PURCHASE", "bones_10", null, null));
        assertThat(lea.getBonusBones()).isNull();
    }

    @Test
    void withoutTheSecretNothingHappens() {
        assertThat(post("Bearer wrong", event("e5", "NON_RENEWING_PURCHASE", "bones_10", null, null))).isEqualTo(401);
        assertThat(post(null, event("e5", "NON_RENEWING_PURCHASE", "bones_10", null, null))).isEqualTo(401);
        assertThat(lea.getBonusBones()).isNull();
    }
}
