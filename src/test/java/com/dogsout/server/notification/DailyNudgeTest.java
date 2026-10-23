package com.dogsout.server.notification;

import com.dogsout.server.bones.BoneStatus;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Which one thing the daily push says. */
class DailyNudgeTest {

    private static DailyDigest digest(int newNearby, int deck, int bones, boolean owner) {
        return new DailyDigest(newNearby, deck, new BoneStatus(bones, 10, 0, Instant.now(), false, 5), owner);
    }

    @Test
    void newPeopleNearbyComeFirst() {
        var nudge = DailyNudgeService.choose(digest(3, 8, 10, true), DayOfWeek.MONDAY);
        assertThat(nudge).isInstanceOf(DailyNudgeService.Nudge.NewNearby.class);
        assertThat(nudge.text("de", 10)[0]).isEqualTo("3 neue Hundebesitzer in deiner Nähe 🐾");
        assertThat(nudge.text("en", 10)[0]).isEqualTo("3 new dog owners near you 🐾");
        assertThat(new DailyNudgeService.Nudge.NewNearby(1).text("de", 10)[0]).isEqualTo("Ein neuer Hundebesitzer in deiner Nähe 🐾");
    }

    @Test
    void otherwiseTheBonesAreBack() {
        var nudge = DailyNudgeService.choose(digest(0, 5, 10, true), DayOfWeek.MONDAY);
        assertThat(nudge).isInstanceOf(DailyNudgeService.Nudge.BonesBack.class);
        assertThat(nudge.screen()).isEqualTo("Discover");
        assertThat(nudge.text("de", 20)[0]).isEqualTo("Deine 20 Knochen sind wieder da 🦴");
    }

    @Test
    void anEmptyDeckGetsNoBonesPushButAPlaydateIdeaBeforeTheWeekend() {
        assertThat(DailyNudgeService.choose(digest(0, 0, 10, true), DayOfWeek.MONDAY)).isNull();
        var nudge = DailyNudgeService.choose(digest(0, 0, 10, true), DayOfWeek.THURSDAY);
        assertThat(nudge).isInstanceOf(DailyNudgeService.Nudge.PlanPlaydate.class);
        assertThat(nudge.screen()).isEqualTo("CreatePlaydate");
    }

    @Test
    void sittersWithoutADogAreNotToldToHost() {
        assertThat(DailyNudgeService.choose(digest(0, 0, 10, false), DayOfWeek.FRIDAY)).isNull();
    }

    @Test
    void onlyAppsThatKnowBonesGetTheNudge() {
        assertThat(DailyNudgeService.knowsBones("1.4.0 (30)")).isTrue();
        assertThat(DailyNudgeService.knowsBones("1.3.0 (29)")).isFalse();
        assertThat(DailyNudgeService.knowsBones(null)).isFalse();
    }
}
