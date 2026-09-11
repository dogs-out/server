package com.dogsout.server.user;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A status is a claim with a shelf life, and for four of the six it can carry a
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
    void onlyTheStatusesAboutAPlaceMayCarryOne() {
        // Home and busy are about availability. Broadcasting where someone lives is
        // the one thing this app has no business doing.
        assertThat(WalkStatus.WALKING.mayShareLocation()).isTrue();
        assertThat(WalkStatus.AT_THE_PARK.mayShareLocation()).isTrue();
        assertThat(WalkStatus.SITTING.mayShareLocation()).isTrue();
        assertThat(WalkStatus.ON_VACATION.mayShareLocation()).isTrue();
        assertThat(WalkStatus.AT_HOME.mayShareLocation()).isFalse();
        assertThat(WalkStatus.BUSY.mayShareLocation()).isFalse();
    }

    @Test
    void beingOutAndAboutIsNarrowerThanHavingAPlace() {
        // A holiday has a place but is not an invitation to come and find you, so
        // it stays off the who's-outside list and cannot send an invite.
        assertThat(WalkStatus.WALKING.isOutAndAbout()).isTrue();
        assertThat(WalkStatus.AT_THE_PARK.isOutAndAbout()).isTrue();
        assertThat(WalkStatus.SITTING.isOutAndAbout()).isTrue();
        assertThat(WalkStatus.ON_VACATION.isOutAndAbout()).isFalse();
        assertThat(WalkStatus.AT_HOME.isOutAndAbout()).isFalse();
        assertThat(WalkStatus.BUSY.isOutAndAbout()).isFalse();
    }

    @Test
    void onlySittingBorrowsSomeoneElsesDog() {
        for (WalkStatus status : WalkStatus.values()) {
            assertThat(status.needsSatDog()).isEqualTo(status == WalkStatus.SITTING);
        }
    }

    @Test
    void aWalkIsAnAfternoonAndAHolidayIsWeeks() {
        assertThat(WalkStatus.WALKING.minHours()).isEqualTo(1);
        assertThat(WalkStatus.WALKING.maxHours()).isEqualTo(5);
        assertThat(WalkStatus.AT_THE_PARK.maxHours()).isEqualTo(5);
        assertThat(WalkStatus.ON_VACATION.minHours()).isEqualTo(72);   // 3 days
        assertThat(WalkStatus.ON_VACATION.maxHours()).isEqualTo(504);  // 21 days
    }

    @Test
    void aDurationOutsideTheRangeIsClampedRatherThanRefused() {
        // A slider and a server that disagree by an hour should not cost someone
        // their status; the range is a guard rail, not a gate.
        assertThat(WalkStatus.WALKING.clampHours(99)).isEqualTo(5);
        assertThat(WalkStatus.WALKING.clampHours(0)).isEqualTo(1);
        assertThat(WalkStatus.WALKING.clampHours(3)).isEqualTo(3);
        assertThat(WalkStatus.ON_VACATION.clampHours(1)).isEqualTo(72);
        assertThat(WalkStatus.ON_VACATION.clampHours(9999)).isEqualTo(504);
    }

    @Test
    void noHoursAtAllMeansTheShortestTheStatusAllows() {
        assertThat(WalkStatus.WALKING.clampHours(null)).isEqualTo(1);
        assertThat(WalkStatus.ON_VACATION.clampHours(null)).isEqualTo(72);
    }

    @Test
    void everyRangeIsOrdered() {
        for (WalkStatus status : WalkStatus.values()) {
            assertThat(status.minHours())
                    .as("%s min <= max", status)
                    .isLessThanOrEqualTo(status.maxHours());
            assertThat(status.minHours()).as("%s min is positive", status).isPositive();
        }
    }
}
