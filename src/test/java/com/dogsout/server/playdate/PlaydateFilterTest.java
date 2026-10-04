package com.dogsout.server.playdate;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Playdate feed filter: weekday and time of day by start, in Swiss time. */
class PlaydateFilterTest {

    // Saturday 3 Oct 2026, 10:30 in Zurich (summer time, UTC+2).
    static final Instant SAT_MORNING = Instant.parse("2026-10-03T08:30:00Z");
    // Tuesday 1 Dec 2026, 17:00 in Zurich (winter time, UTC+1).
    static final Instant TUE_EVENING = Instant.parse("2026-12-01T16:00:00Z");

    @Test
    void noFilterKeepsEverything() {
        assertThat(PlaydateService.startsWithin(SAT_MORNING, null, null)).isTrue();
        assertThat(PlaydateService.startsWithin(SAT_MORNING, List.of(), List.of())).isTrue();
    }

    @Test
    void matchesDayAndTimeInSwissTime() {
        assertThat(PlaydateService.startsWithin(SAT_MORNING, List.of("Saturday"), List.of("Morning"))).isTrue();
        assertThat(PlaydateService.startsWithin(SAT_MORNING, List.of("Sunday"), List.of())).isFalse();
        assertThat(PlaydateService.startsWithin(SAT_MORNING, List.of(), List.of("Evening"))).isFalse();
        assertThat(PlaydateService.startsWithin(TUE_EVENING, List.of("Tuesday", "Friday"), List.of("Evening"))).isTrue();
        assertThat(PlaydateService.startsWithin(TUE_EVENING, List.of(), List.of("Afternoon"))).isFalse();
    }
}
