package com.dogsout.server.school;

import com.dogsout.server.auth.EmailService;
import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.school.SchoolDtos.EventRequest;
import com.dogsout.server.school.SchoolDtos.SchoolRequest;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import com.dogsout.server.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dog schools end to end, against a real (H2) database so foreign keys are enforced. */
@SpringBootTest
@Transactional
class SchoolFlowTest {

    @Autowired SchoolService schools;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired DogSchoolRepository schoolRepo;
    @MockitoBean PushNotificationService push;
    @MockitoBean EmailService email;

    User user(String name) {
        User u = new User();
        u.setEmail(name.toLowerCase() + "@school.test");
        u.setName(name);
        u.setPassword("x");
        u.setDateOfBirth(LocalDate.of(1990, 1, 1));
        u.setLatitude(47.40);
        u.setLongitude(8.55);
        return users.save(u);
    }

    SchoolRequest school() {
        return new SchoolRequest("Hundeschule Pfote", "Welpenkurse und mehr", "Bahnhofstrasse 1, 8152 Opfikon",
                47.43, 8.57, "044 123 45 67", "pfote.ch", "Mo–Fr 9–18");
    }

    EventRequest event(Integer places) {
        Instant start = Instant.now().plus(Duration.ofDays(3));
        return new EventRequest("Welpenkurs", null, start, start.plus(Duration.ofHours(1)),
                null, null, null, null, 4500, places);
    }

    @Test
    void aSchoolIsHiddenUntilApproved() {
        User owner = user("Schule"), visitor = user("Lea");
        var mine = schools.register(owner.getEmail(), school());
        assertThat(mine.status()).isEqualTo("PENDING");
        assertThat(mine.website()).isEqualTo("https://pfote.ch");
        assertThat(schools.nearby(visitor.getEmail())).isEmpty();
        assertThatThrownBy(() -> schools.get(visitor.getEmail(), mine.id())).isInstanceOf(ResponseStatusException.class);
        // No events before approval either.
        assertThatThrownBy(() -> schools.createEvent(owner.getEmail(), event(null))).isInstanceOf(ResponseStatusException.class);

        schools.review(mine.id(), "APPROVED", null);
        assertThat(schools.nearby(visitor.getEmail())).extracting(SchoolDtos.SchoolResponse::name).containsExactly("Hundeschule Pfote");
    }

    @Test
    void signingUpRespectsThePlaces() {
        User owner = user("Schule"), lea = user("Lea"), moritz = user("Moritz");
        var mine = schools.register(owner.getEmail(), school());
        schools.review(mine.id(), "APPROVED", null);
        var ev = schools.createEvent(owner.getEmail(), event(1));
        // At the school when no place of its own is given.
        assertThat(ev.address()).isEqualTo("Bahnhofstrasse 1, 8152 Opfikon");
        assertThat(schools.upcomingNearby(lea.getEmail())).hasSize(1);

        assertThat(schools.join(lea.getEmail(), ev.id()).joined()).isTrue();
        assertThatThrownBy(() -> schools.join(moritz.getEmail(), ev.id()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("full");
        assertThat(schools.participantsOf(owner.getEmail(), ev.id())).extracting(SchoolDtos.ParticipantResponse::name)
                .containsExactly("Lea");
        // Only the school sees who signed up.
        assertThatThrownBy(() -> schools.participantsOf(lea.getEmail(), ev.id())).isInstanceOf(ResponseStatusException.class);

        schools.leave(lea.getEmail(), ev.id());
        assertThat(schools.join(moritz.getEmail(), ev.id()).participantCount()).isEqualTo(1);
    }

    @Test
    void aCancelledEventDisappearsFromTheList() {
        User owner = user("Schule"), lea = user("Lea");
        var mine = schools.register(owner.getEmail(), school());
        schools.review(mine.id(), "APPROVED", null);
        var ev = schools.createEvent(owner.getEmail(), event(null));
        schools.cancelEvent(owner.getEmail(), ev.id());
        assertThat(schools.upcomingNearby(lea.getEmail())).isEmpty();
    }

    @Test
    void deletingTheSchoolAccountTakesEverythingWithIt() {
        User owner = user("Schule"), lea = user("Lea");
        var mine = schools.register(owner.getEmail(), school());
        schools.review(mine.id(), "APPROVED", null);
        var ev = schools.createEvent(owner.getEmail(), event(null));
        schools.join(lea.getEmail(), ev.id());

        userService.deleteAccount(owner.getEmail());
        assertThat(schoolRepo.findAll()).isEmpty();
        assertThat(schools.upcomingNearby(lea.getEmail())).isEmpty();
    }
}
