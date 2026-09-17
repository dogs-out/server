package com.dogsout.server.sitter;

import com.dogsout.server.user.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shape of the late-cancellation rule.
 *
 * <p>The numbers live on the entity so that the server, the warning the sitter
 * sees before confirming, and this test all read the same constants. A rule the
 * app describes differently from how it is enforced is worse than no rule.
 */
class SitterCancellationRulesTest {

    /** Mirrors the check in SitterService.cancelAsSitter. */
    private static boolean isLate(Instant startsAt, Instant now) {
        return startsAt != null
                && startsAt.isBefore(now.plus(SitterCancellation.LATE_HOURS, ChronoUnit.HOURS));
    }

    @Test
    void theRuleIsThreeStrikesAndThirtyDays() {
        // The wording in four languages promises exactly these.
        assertThat(SitterCancellation.LATE_HOURS).isEqualTo(24);
        assertThat(SitterCancellation.STRIKES).isEqualTo(3);
        assertThat(SitterCancellation.PENALTY_DAYS).isEqualTo(30);
    }

    @Test
    void cancellingInsideTheDayBeforeIsLate() {
        Instant now = Instant.now();
        assertThat(isLate(now.plus(2, ChronoUnit.HOURS), now)).isTrue();
        assertThat(isLate(now.plus(23, ChronoUnit.HOURS), now)).isTrue();
    }

    @Test
    void cancellingWithMoreThanADayToGoIsNot() {
        Instant now = Instant.now();
        // Doing the right thing early must never cost anything, or people learn
        // to say nothing until it is too late — the exact failure this prevents.
        assertThat(isLate(now.plus(25, ChronoUnit.HOURS), now)).isFalse();
        assertThat(isLate(now.plus(7, ChronoUnit.DAYS), now)).isFalse();
    }

    @Test
    void aSittingAlreadyUnderwayCountsAsLate() {
        Instant now = Instant.now();
        assertThat(isLate(now.minus(1, ChronoUnit.HOURS), now)).isTrue();
    }

    @Test
    void aRequestWithNoStartIsNeverLate() {
        assertThat(isLate(null, Instant.now())).isFalse();
    }

    // ─── The penalty on the user ──────────────────────────────────────────────

    @Test
    void aFutureBlockDateMeansBlocked() {
        User sitter = new User();
        sitter.setSitterBlockedUntil(Instant.now().plus(5, ChronoUnit.DAYS));
        assertThat(sitter.isSitterBlocked()).isTrue();
    }

    @Test
    void aPastBlockDateHasExpiredOnItsOwn() {
        User sitter = new User();
        sitter.setSitterBlockedUntil(Instant.now().minus(1, ChronoUnit.SECONDS));
        // Nothing runs to lift it; the date simply stops being in the future.
        assertThat(sitter.isSitterBlocked()).isFalse();
    }

    @Test
    void aSitterWhoNeverCancelledIsNotBlocked() {
        assertThat(new User().isSitterBlocked()).isFalse();
    }

    @Test
    void thePenaltyDoesNotTouchTheirOwnSitterToggle() {
        // The role is suspended, not decided for them — it comes back by itself.
        User sitter = new User();
        sitter.setIsSitter(true);
        sitter.setSitterBlockedUntil(Instant.now().plus(30, ChronoUnit.DAYS));
        assertThat(sitter.getIsSitter()).isTrue();
    }
}
