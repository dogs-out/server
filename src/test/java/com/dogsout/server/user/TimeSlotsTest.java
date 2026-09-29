package com.dogsout.server.user;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimeSlotsTest {

    @Test
    void storesKnownSlotsInDayOrderAndDropsTheRest() {
        assertThat(TimeSlots.join(List.of("evening", "Night", "Morning"))).isEqualTo("Morning||Evening");
    }

    @Test
    void storesNothingWhenNothingValidIsPicked() {
        assertThat(TimeSlots.join(List.of())).isNull();
        assertThat(TimeSlots.join(List.of("Midnight"))).isNull();
    }

    @Test
    void matchesASitterFreeAtAnyOfTheAskedTimes() {
        assertThat(TimeSlots.anyMatch("Morning||Evening", List.of("Afternoon", "Evening"))).isTrue();
        assertThat(TimeSlots.anyMatch("Morning", List.of("Afternoon", "Evening"))).isFalse();
    }

    @Test
    void askingForNothingShowsEveryone() {
        assertThat(TimeSlots.anyMatch("Morning", null)).isTrue();
        assertThat(TimeSlots.anyMatch("Morning", List.of())).isTrue();
    }

    @Test
    void aSitterWhoNamedNoTimesStaysInTheList() {
        assertThat(TimeSlots.anyMatch(null, List.of("Evening"))).isTrue();
    }
}
