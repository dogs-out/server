package com.dogsout.server;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklySeriesTest {

    private static ZonedDateTime at(int y, int m, int d, int h, int min) {
        return LocalDateTime.of(y, m, d, h, min).atZone(WeeklySeries.ZONE);
    }

    // Tuesday 3 Nov 2026 13:00–14:00, every Tuesday and Friday until Fri 27 Nov.
    private final ZonedDateTime first = at(2026, 11, 3, 13, 0);
    private final EnumSet<DayOfWeek> days = EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY);

    @Test
    void theSeriesEndsWithItsLastOccurrence() {
        var end = WeeklySeries.lastEnd(first.toInstant(), first.plusHours(1).toInstant(), days, LocalDate.of(2026, 11, 29));
        assertThat(end).isEqualTo(at(2026, 11, 27, 14, 0).toInstant());
    }

    @Test
    void theNextOccurrenceSkipsPastOnes() {
        var end = at(2026, 11, 27, 14, 0).toInstant();
        // Wednesday morning: next is Friday 6 Nov.
        assertThat(WeeklySeries.nextStart(first.toInstant(), end, "TUESDAY,FRIDAY", 60, at(2026, 11, 4, 9, 0).toInstant()))
                .isEqualTo(at(2026, 11, 6, 13, 0).toInstant());
        // During Tuesday's class it is still that class.
        assertThat(WeeklySeries.nextStart(first.toInstant(), end, "TUESDAY,FRIDAY", 60, at(2026, 11, 10, 13, 30).toInstant()))
                .isEqualTo(at(2026, 11, 10, 13, 0).toInstant());
        // After the last one, nothing.
        assertThat(WeeklySeries.nextStart(first.toInstant(), end, "TUESDAY,FRIDAY", 60, at(2026, 11, 27, 15, 0).toInstant()))
                .isNull();
    }

    @Test
    void wallClockTimeHoldsAcrossTheClockChange() {
        // A class at 13:00 in October is still at 13:00 after the switch to winter time.
        var oct = at(2026, 10, 20, 13, 0);
        var next = WeeklySeries.nextStart(oct.toInstant(), at(2026, 11, 30, 14, 0).toInstant(), "TUESDAY", 60,
                at(2026, 11, 2, 0, 0).toInstant());
        assertThat(next.atZone(WeeklySeries.ZONE).getHour()).isEqualTo(13);
    }
}
