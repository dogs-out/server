package com.dogsout.server.sitter;

import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Open jobs respect the sitter's search distance, like the owner and sitter lists. */
@SpringBootTest
@Transactional
class OpenJobsDistanceTest {

    @Autowired SitterService sitters;
    @Autowired UserRepository users;
    @Autowired SittingRequestRepository requests;
    @MockitoBean PushNotificationService push;

    User user(String name, Double lat, Double lng) {
        User u = new User();
        u.setEmail(name.toLowerCase() + "@test.ch");
        u.setName(name);
        u.setPassword("x");
        u.setDateOfBirth(LocalDate.of(1990, 1, 1));
        u.setLatitude(lat);
        u.setLongitude(lng);
        return users.save(u);
    }

    void job(User owner) {
        SittingRequest r = new SittingRequest();
        r.setOwner(owner);
        r.setStartsAt(Instant.now().plus(2, ChronoUnit.DAYS));
        r.setEndsAt(Instant.now().plus(3, ChronoUnit.DAYS));
        r.setDogIds("");
        requests.save(r);
    }

    @Test
    void onlyJobsWithinTheSearchDistanceAreShown() {
        User sitter = user("Sitter", 47.3769, 8.5417);          // Zürich
        job(user("Nearby", 47.4200, 8.5600));                    // ~5 km
        job(user("Bern", 46.9480, 7.4474));                      // ~95 km
        job(user("Cupertino", 37.3349, -122.0090));              // ~9,400 km

        assertThat(sitters.openRequests(sitter.getEmail())).hasSize(1);   // default 50 km

        sitter.setMaxDistanceKm(150);
        users.save(sitter);
        assertThat(sitters.openRequests(sitter.getEmail())).hasSize(2);
    }

    @Test
    void withoutASavedLocationNothingIsFiltered() {
        User sitter = user("NoLocation", null, null);
        job(user("Nearby", 47.4200, 8.5600));
        job(user("Cupertino", 37.3349, -122.0090));
        assertThat(sitters.openRequests(sitter.getEmail())).hasSize(2);
    }
}
