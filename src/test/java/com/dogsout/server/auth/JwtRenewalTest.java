package com.dogsout.server.auth;

import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Sessions renew as they are used.
 *
 * <p>Before this, tokens simply expired 24 hours after sign-in with nothing to
 * refresh them, so anyone who had been away for a day was thrown back to the login
 * screen. These cover the renewal decision and the one case where it must not
 * happen — a token a password change has already revoked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtRenewalTest {

    private static final String SECRET = "test-secret-key-long-enough-for-hmac-sha-256-signing!!";
    private static final String EMAIL = "lena@example.com";
    private static final long THIRTY_DAYS = Duration.ofDays(30).toMillis();
    private static final long ONE_DAY = Duration.ofDays(1).toMillis();

    @Mock private UserDetailsService userDetailsService;
    @Mock private UserRepository userRepository;

    private JwtUtil jwtUtil;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", THIRTY_DAYS);
        ReflectionTestUtils.setField(jwtUtil, "refreshAfter", ONE_DAY);
        filter = new JwtAuthFilter(jwtUtil, userDetailsService, userRepository);
    }

    /** A token as it would have been issued at some point in the past. */
    private String tokenIssuedAt(Instant issuedAt) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(EMAIL)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusMillis(THIRTY_DAYS)))
                .signWith(key)
                .compact();
    }

    private MockHttpServletResponse filterWith(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private void givenTheAccountExists(Instant passwordChangedAt) {
        User user = new User();
        user.setEmail(EMAIL);
        user.setPasswordChangedAt(passwordChangedAt);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserByUsername(anyString()))
                .thenReturn(org.springframework.security.core.userdetails.User
                        .withUsername(EMAIL).password("x").roles("USER").build());
    }

    @Test
    void leavesAFreshTokenAlone() {
        assertThat(jwtUtil.isDueForRenewal(tokenIssuedAt(Instant.now()))).isFalse();
    }

    @Test
    void renewsOnceTheTokenIsOlderThanTheThreshold() {
        assertThat(jwtUtil.isDueForRenewal(tokenIssuedAt(Instant.now().minus(Duration.ofDays(2))))).isTrue();
    }

    @Test
    void handsBackAReplacementForAnAgeingToken() throws Exception {
        givenTheAccountExists(null);

        MockHttpServletResponse response = filterWith(tokenIssuedAt(Instant.now().minus(Duration.ofDays(2))));

        String renewed = response.getHeader(JwtAuthFilter.REFRESHED_TOKEN_HEADER);
        assertThat(renewed).isNotNull();
        assertThat(jwtUtil.extractEmail(renewed)).isEqualTo(EMAIL);
        assertThat(jwtUtil.isDueForRenewal(renewed)).isFalse();
    }

    @Test
    void sendsNoReplacementForATokenThatIsStillYoung() throws Exception {
        givenTheAccountExists(null);

        MockHttpServletResponse response = filterWith(tokenIssuedAt(Instant.now()));

        assertThat(response.getHeader(JwtAuthFilter.REFRESHED_TOKEN_HEADER)).isNull();
    }

    /**
     * The point of the password-change revocation is to cut off a session someone
     * else is holding. Renewing that session on its way out would undo it.
     */
    @Test
    void refusesToRenewATokenRevokedByAPasswordChange() throws Exception {
        givenTheAccountExists(Instant.now());

        MockHttpServletResponse response = filterWith(tokenIssuedAt(Instant.now().minus(Duration.ofDays(2))));

        assertThat(response.getHeader(JwtAuthFilter.REFRESHED_TOKEN_HEADER)).isNull();
    }
}
