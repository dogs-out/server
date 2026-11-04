package com.dogsout.server.matching;

import com.dogsout.server.user.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The star next to a Premium member's name, which they can switch off. */
class PremiumBadgeTest {

    @Test
    void premiumMembersShowTheStarUnlessTheyTurnItOff() {
        User u = new User();
        assertThat(DiscoverService.premiumBadge(u)).isFalse();

        u.setPremiumUntil(Instant.now().plus(Duration.ofDays(20)));
        assertThat(DiscoverService.premiumBadge(u)).isTrue();

        u.setShowPremiumBadge(false);
        assertThat(DiscoverService.premiumBadge(u)).isFalse();
    }

    @Test
    void anExpiredPremiumShowsNoStar() {
        User u = new User();
        u.setPremiumUntil(Instant.now().minusSeconds(60));
        assertThat(DiscoverService.premiumBadge(u)).isFalse();
    }
}
