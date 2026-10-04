package com.dogsout.server.user;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityTest {

    private static User sitterWithGrid(String grid) {
        User u = new User();
        u.setSitterAvailability(grid);
        return u;
    }

    @Test
    void storesPairsInWeekOrderAndDropsMalformedOnes() {
        assertThat(Availability.join(List.of("wednesday:afternoon", "Monday:Morning", "Funday:Morning", "Monday")))
                .isEqualTo("Monday:Morning||Wednesday:Afternoon");
        assertThat(Availability.join(List.of())).isNull();
    }

    @Test
    void derivesTheOldDayAndTimeListsFromTheGrid() {
        String grid = "Monday:Morning||Wednesday:Afternoon";
        assertThat(Availability.days(grid)).containsExactly("Monday", "Wednesday");
        assertThat(Availability.slots(grid)).containsExactly("Morning", "Afternoon");
    }

    @Test
    void mondayMorningIsNotMondayAfternoon() {
        // The whole point of the grid: the two lists alone would have said yes here.
        User sitter = sitterWithGrid("Monday:Morning||Wednesday:Afternoon");
        assertThat(Availability.matches(sitter, List.of("Monday"), List.of("Afternoon"))).isFalse();
        assertThat(Availability.matches(sitter, List.of("Monday"), List.of("Morning"))).isTrue();
    }

    @Test
    void anEmptyListInTheFilterMeansAny() {
        User sitter = sitterWithGrid("Wednesday:Evening");
        assertThat(Availability.matches(sitter, List.of("Wednesday"), List.of())).isTrue();
        assertThat(Availability.matches(sitter, List.of(), List.of("Evening"))).isTrue();
        assertThat(Availability.matches(sitter, List.of(), List.of())).isTrue();
        assertThat(Availability.matches(sitter, List.of("Thursday"), List.of())).isFalse();
    }

    @Test
    void aSitterWithoutAGridFallsBackToTheOldLists() {
        User legacy = new User();
        legacy.setSitterWeekdays("Monday||Friday");
        legacy.setSitterTimeSlots("Evening");
        assertThat(Availability.matches(legacy, List.of("Friday"), List.of("Evening"))).isTrue();
        assertThat(Availability.matches(legacy, List.of("Tuesday"), List.of())).isFalse();
        // Named nothing at all: stays listed, as before.
        assertThat(Availability.matches(new User(), List.of("Tuesday"), List.of("Morning"))).isTrue();
    }
}
