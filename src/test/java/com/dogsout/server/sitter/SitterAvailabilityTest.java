package com.dogsout.server.sitter;

import com.dogsout.server.matching.DiscoverService;
import com.dogsout.server.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who survives the weekday filter.
 *
 * <p>The awkward case is a sitter who named no days. Dropping them would hide
 * people who are simply willing to be asked, which is most new sitters — so
 * silence has to read as "ask me", not as "never".
 */
class SitterAvailabilityTest {

    private static boolean available(String namedDays, String asked) {
        User sitter = new User();
        sitter.setSitterWeekdays(namedDays);
        return Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(DiscoverService.class, "availableOn", sitter, asked));
    }

    @Test
    void askingForNoParticularDayKeepsEveryone() {
        assertThat(available("Monday||Tuesday", null)).isTrue();
        assertThat(available("Monday||Tuesday", "")).isTrue();
        assertThat(available(null, null)).isTrue();
    }

    @Test
    void aSitterFreeThatDayIsKept() {
        assertThat(available("Monday||Thursday||Sunday", "Thursday")).isTrue();
        assertThat(available("Monday", "Monday")).isTrue();
    }

    @Test
    void aSitterNotFreeThatDayIsDropped() {
        assertThat(available("Monday||Tuesday", "Saturday")).isFalse();
    }

    @Test
    void namingNoDaysMeansAskMeRatherThanNever() {
        // Most sitters never fill this in; dropping them would empty the list.
        assertThat(available(null, "Saturday")).isTrue();
        assertThat(available("", "Saturday")).isTrue();
    }

    @Test
    void caseAndSpacingDoNotChangeTheAnswer() {
        assertThat(available("Monday||Friday", " friday ")).isTrue();
        assertThat(available("monday", "Monday")).isTrue();
    }
}
