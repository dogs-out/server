package com.dogsout.server.sitter;

import com.dogsout.server.matching.DiscoverService;
import com.dogsout.server.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who survives the weekday filter.
 *
 * <p>The awkward case is a sitter who named no days. Dropping them would hide
 * people who are simply willing to be asked, which is most new sitters — so
 * silence has to read as "ask me", not as "never".
 *
 * <p>The second awkward case is several days at once. Matching on any of them
 * rather than all is what the person picking them means: "Monday and Friday" is
 * somebody with two days to cover, not somebody who needs one sitter for both.
 */
class SitterAvailabilityTest {

    private static boolean available(String namedDays, List<String> asked) {
        User sitter = new User();
        sitter.setSitterWeekdays(namedDays);
        return Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(DiscoverService.class, "availableOn", sitter, asked));
    }

    private static boolean available(String namedDays, String asked) {
        return available(namedDays, asked == null ? null : List.of(asked));
    }

    @Test
    void askingForNoParticularDayKeepsEveryone() {
        assertThat(available("Monday||Tuesday", (List<String>) null)).isTrue();
        assertThat(available("Monday||Tuesday", List.of())).isTrue();
        assertThat(available("Monday||Tuesday", "")).isTrue();
        assertThat(available(null, (List<String>) null)).isTrue();
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

    // ─── Several days at once ─────────────────────────────────────────────────

    @Test
    void oneMatchingDayOutOfSeveralIsEnough() {
        // Asked for Monday and Friday; this sitter only does Fridays, and is
        // exactly who the person asking wants to see.
        assertThat(available("Friday", List.of("Monday", "Friday"))).isTrue();
        assertThat(available("Monday", List.of("Monday", "Friday"))).isTrue();
    }

    @Test
    void matchingNoneOfThemIsDropped() {
        assertThat(available("Tuesday||Wednesday", List.of("Monday", "Friday"))).isFalse();
    }

    @Test
    void everyDayExceptOneIsSixDaysAsked() {
        // "Every day except Saturday" is how someone picks six days, and a
        // Saturday-only sitter is the one person it should leave out.
        List<String> exceptSaturday =
                List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Sunday");
        assertThat(available("Saturday", exceptSaturday)).isFalse();
        assertThat(available("Saturday||Sunday", exceptSaturday)).isTrue();
    }

    @Test
    void blankEntriesAmongThemAreIgnored() {
        // A trailing empty value should not quietly turn the filter off.
        assertThat(available("Tuesday", List.of("Monday", " ", "Friday"))).isFalse();
        assertThat(available("Monday", List.of("Monday", " "))).isTrue();
    }

    @Test
    void onlyBlanksReadsAsNoFilterAtAll() {
        assertThat(available("Tuesday", List.of(" ", ""))).isTrue();
    }

    @Test
    void silenceStillBeatsEveryCombination() {
        assertThat(available(null, List.of("Monday", "Friday"))).isTrue();
    }
}
