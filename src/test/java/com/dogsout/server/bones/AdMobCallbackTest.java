package com.dogsout.server.bones;

import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A bone for a watched ad, but only when Google says so — and only once per ad. */
class AdMobCallbackTest {

    private KeyPair googleKey;
    private final AdMobVerifier verifier = new AdMobVerifier(new ObjectMapper());
    private final AdRewardTransactionRepository transactions = mock(AdRewardTransactionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BoneService bones = new BoneService(users, Clock.systemUTC());
    private final AdMobCallbackController controller =
            new AdMobCallbackController(verifier, transactions, users, bones);
    private final User lea = new User();

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        googleKey = gen.generateKeyPair();
        verifier.putKey(3335741209L, googleKey.getPublic());
        lea.setId(2L);
        when(users.findById(2L)).thenReturn(Optional.of(lea));
    }

    private String signed(String query, KeyPair key) throws Exception {
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(key.getPrivate());
        s.update(query.getBytes(StandardCharsets.UTF_8));
        String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
        return query + "&signature=" + sig + "&key_id=3335741209";
    }

    private int call(String rawQuery) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/bones/admob-ssv");
        req.setQueryString(rawQuery);
        return controller.callback(req).getStatusCode().value();
    }

    private static final String QUERY = "ad_network=5450213213286189855&ad_unit=1234&reward_amount=1"
            + "&reward_item=bone&timestamp=1730000000000&transaction_id=abc123&user_id=2";

    @Test
    void aSignedCallbackGrantsOneBone() throws Exception {
        assertThat(call(signed(QUERY, googleKey))).isEqualTo(200);
        assertThat(lea.getBonusBones()).isEqualTo(1);
        verify(transactions).save(any());
    }

    @Test
    void aForgedCallbackGrantsNothing() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        assertThat(call(signed(QUERY, gen.generateKeyPair()))).isEqualTo(403);
        assertThat(lea.getBonusBones()).isNull();
    }

    @Test
    void theSameAdPaysOnlyOnce() throws Exception {
        when(transactions.existsById("abc123")).thenReturn(true);
        assertThat(call(signed(QUERY, googleKey))).isEqualTo(200);
        assertThat(lea.getBonusBones()).isNull();
        verify(transactions, never()).save(any());
    }

    @Test
    void admobsUrlCheckWithoutParametersGetsA200() {
        assertThat(call(null)).isEqualTo(200);
    }
}
