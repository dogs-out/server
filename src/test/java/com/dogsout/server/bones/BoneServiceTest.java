package com.dogsout.server.bones;

import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class BoneServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    // 21:30 in Zurich: late enough that midnight is close, which is where a day bug would show.
    private final Instant evening = ZonedDateTime.of(2026, 11, 3, 21, 30, 0, 0, BoneService.ZONE).toInstant();

    private BoneService at(Instant now) {
        return new BoneService(users, Clock.fixed(now, BoneService.ZONE));
    }

    @Test
    void tenADayThenOutOfBones() {
        User u = new User();
        BoneService bones = at(evening);
        for (int i = 0; i < 10; i++) bones.spend(u);

        assertThat(bones.status(u).remaining()).isZero();
        assertThatThrownBy(() -> bones.spend(u))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(BoneService.OUT_OF_BONES);
    }

    @Test
    void theAllowanceRefillsAtSwissMidnight() {
        User u = new User();
        for (int i = 0; i < 10; i++) at(evening).spend(u);

        // 23:59 is still the same day…
        assertThat(at(evening.plus(Duration.ofMinutes(149))).status(u).remaining()).isZero();
        // …00:01 is the next one.
        assertThat(at(evening.plus(Duration.ofMinutes(151))).status(u).remaining()).isEqualTo(10);
        assertThat(at(evening).status(u).refillsAt())
                .isEqualTo(ZonedDateTime.of(2026, 11, 4, 0, 0, 0, 0, BoneService.ZONE).toInstant());
    }

    @Test
    void premiumGetsTwenty() {
        User u = new User();
        u.setPremiumUntil(evening.plus(Duration.ofDays(30)));
        assertThat(at(evening).status(u).remaining()).isEqualTo(20);
        assertThat(at(evening).status(u).premium()).isTrue();
    }

    @Test
    void extraBonesAreSpentAfterTheDailyOnesAndKept() {
        User u = new User();
        BoneService bones = at(evening);
        bones.grant(u, 3);
        assertThat(bones.status(u).remaining()).isEqualTo(13);

        for (int i = 0; i < 11; i++) bones.spend(u);
        assertThat(u.getBonusBones()).isEqualTo(2);
        // The next day brings ten new ones on top of the two left over.
        assertThat(at(evening.plus(Duration.ofHours(4))).status(u).remaining()).isEqualTo(12);
    }

    @Test
    void adsTopUpAtMostFiveADay() {
        User u = new User();
        BoneService bones = at(evening);
        for (int i = 0; i < BoneService.MAX_AD_REWARDS_PER_DAY; i++) bones.rewardAd(u);

        assertThat(bones.status(u).adRewardsLeft()).isZero();
        assertThatThrownBy(() -> bones.rewardAd(u)).isInstanceOf(ResponseStatusException.class);
        assertThat(at(evening.plus(Duration.ofHours(4))).status(u).adRewardsLeft()).isEqualTo(5);
    }

    @Test
    void appsBefore14DoNotPay() {
        assertThat(AppVersion.isOlderThan("1.3.0 (29)", 1, 4)).isTrue();
        assertThat(AppVersion.isOlderThan("1.4.0 (30)", 1, 4)).isFalse();
        assertThat(AppVersion.isOlderThan("2.0.0", 1, 4)).isFalse();
        // No header: an app from before 1.3, which never sent one.
        assertThat(AppVersion.isOlderThan(null, 1, 4)).isTrue();
    }
}
