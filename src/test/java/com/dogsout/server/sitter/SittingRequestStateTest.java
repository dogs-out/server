package com.dogsout.server.sitter;

import com.dogsout.server.user.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two derived states the whole feature turns on.
 *
 * <p>Both are deliberately computed rather than stored. "Over" is a fact about the
 * clock, so a status column recording it would be a column that is wrong between
 * the moment a window closes and the next time something writes to the row.
 * "Accepted" is a sitter being present, which avoids adding a value to the status
 * enum — Hibernate leaves the CHECK constraint stale under ddl-auto=update, and
 * that has taken production down here before.
 */
class SittingRequestStateTest {

    private static SittingRequest request(Instant endsAt, User sitter) {
        SittingRequest r = new SittingRequest();
        r.setStartsAt(endsAt == null ? null : endsAt.minus(4, ChronoUnit.HOURS));
        r.setEndsAt(endsAt);
        r.setSitter(sitter);
        return r;
    }

    private static User someone() {
        User u = new User();
        u.setId(7L);
        u.setName("Jonas");
        return u;
    }

    @Test
    void aWindowThatHasPassedIsOver() {
        Instant now = Instant.now();
        assertThat(request(now.minus(1, ChronoUnit.MINUTES), null).isOver(now)).isTrue();
    }

    @Test
    void aWindowStillRunningIsNotOver() {
        Instant now = Instant.now();
        // Started an hour ago, ends in an hour: still perfectly takeable, which is
        // why the job board keys on the end and not on the start.
        assertThat(request(now.plus(1, ChronoUnit.HOURS), null).isOver(now)).isFalse();
    }

    @Test
    void aWindowInTheFutureIsNotOver() {
        Instant now = Instant.now();
        assertThat(request(now.plus(3, ChronoUnit.DAYS), null).isOver(now)).isFalse();
    }

    @Test
    void aRequestWithNoEndIsNeverOver() {
        assertThat(request(null, null).isOver(Instant.now())).isFalse();
    }

    @Test
    void aSitterPresentMeansAccepted() {
        assertThat(request(Instant.now(), someone()).isAccepted()).isTrue();
    }

    @Test
    void closedWithNobodyFoundIsNotAccepted() {
        SittingRequest r = request(Instant.now().plus(1, ChronoUnit.HOURS), null);
        r.setStatus(SittingRequestStatus.CLOSED);
        // The owner gave up rather than picked someone — nothing to rate later.
        assertThat(r.isAccepted()).isFalse();
    }

    @Test
    void theStatusEnumStillHasOnlyTheTwoSafeValues() {
        // A third value here needs the production CHECK constraint dropped first.
        assertThat(SittingRequestStatus.values())
                .containsExactly(SittingRequestStatus.OPEN, SittingRequestStatus.CLOSED);
    }
}
