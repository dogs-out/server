package com.dogsout.server.matching;

import com.dogsout.server.GeoUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoverDistanceTest {

    @Test
    void roundsToTheWholeKilometreTheAppDisplays() {
        assertThat(DiscoverService.coarseDistanceKm(3.847291)).isEqualTo(4.0);
        assertThat(DiscoverService.coarseDistanceKm(1.2)).isEqualTo(1.0);
        assertThat(DiscoverService.coarseDistanceKm(0.4)).isEqualTo(0.0);
    }

    /**
     * The point of the rounding: two people a few hundred metres apart must not be
     * distinguishable through the distance the API returns, or their positions can be
     * solved for by sampling from several coordinates.
     */
    @Test
    void hidesDifferencesSmallerThanAKilometre() {
        // Zurich HB, and two points a short walk apart from it
        double lat = 47.3779, lon = 8.5403;
        double a = GeoUtil.distanceKm(lat, lon, 47.3869, 8.5403);   // ~1.0 km north
        double b = GeoUtil.distanceKm(lat, lon, 47.3872, 8.5403);   // ~1.03 km north

        assertThat(a).isNotEqualTo(b);
        assertThat(DiscoverService.coarseDistanceKm(a))
                .isEqualTo(DiscoverService.coarseDistanceKm(b));
    }

    @Test
    void keepsFarApartUsersDistinguishable() {
        double near = DiscoverService.coarseDistanceKm(2.1);
        double far = DiscoverService.coarseDistanceKm(11.8);

        assertThat(near).isLessThan(far);
        assertThat(far).isEqualTo(12.0);
    }
}
