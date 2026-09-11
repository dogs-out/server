package com.dogsout.server.user;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A status is a claim with a shelf life, and for two of the four it can carry a
 * point. Both of those are places where getting it wrong publishes something
 * about someone that they did not agree to, so they are pinned here.
 */
class WalkStatusTest {

    private static User withStatus(WalkStatus status, Instant expiresAt) {
        User u = new User();
        u.setWalkStatus(status);
        u.setWalkStatusExpiresAt(expiresAt);
        return u;
    }

    @Test
    void aStatusWithinItsWindowReadsBack() {
        User u = withStatus(WalkStatus.WALKING, Instant.now().plus(Duration.ofHours(1)));
        assertThat(u.activeWalkStatus()).isEqualTo(WalkStatus.WALKING);
    }

    @Test
    void anExpiredStatusIsNoStatusAtAll() {
        // Nothing sweeps these up, so "expired" has to mean "reads as absent".
        User u = withStatus(WalkStatus.WALKING, Instant.now().minusSeconds(1));
        assertThat(u.activeWalkStatus()).isNull();
    }

    @Test
    void noStatusStaysNullWhateverTheExpiry() {
        assertThat(withStatus(null, Instant.now().plus(Duration.ofDays(1))).activeWalkStatus()).isNull();
    }

    @Test
    void onlyWalkingAndVacationMayCarryAPoint() {
        // Home and busy are about availability. Broadcasting where someone lives is
        // the one thing this app has no business doing.
        assertThat(WalkStatus.WALKING.mayShareLocation()).isTrue();
        assertThat(WalkStatus.ON_VACATION.mayShareLocation()).isTrue();
        assertThat(WalkStatus.AT_HOME.mayShareLocation()).isFalse();
        assertThat(WalkStatus.BUSY.mayShareLocation()).isFalse();
    }
}
