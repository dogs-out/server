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
                null, null, null, null, 4500, places, null, null, null);
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

    @Test
    void theOwnerCanCloseTheSchoolAndOthersCanReportIt() {
        User owner = user("Schule"), lea = user("Lea");
        var mine = schools.register(owner.getEmail(), school());
        schools.review(mine.id(), "APPROVED", null);
        var ev = schools.createEvent(owner.getEmail(), event(null));
        schools.join(lea.getEmail(), ev.id());

        schools.report(lea.getEmail(), mine.id(), new com.dogsout.server.moderation.ReportRequest("Scam or spam", null));
        assertThatThrownBy(() -> schools.report(owner.getEmail(), mine.id(),
                new com.dogsout.server.moderation.ReportRequest("Other", null))).isInstanceOf(ResponseStatusException.class);

        schools.deleteMine(owner.getEmail());
        assertThat(schools.mine(owner.getEmail())).isEmpty();
        assertThat(schools.upcomingNearby(lea.getEmail())).isEmpty();
    }

    @Test
    void aWeeklyCourseStaysListedUntilItsLastClass() {
        User owner = user("Schule"), lea = user("Lea");
        var mine = schools.register(owner.getEmail(), school());
        schools.review(mine.id(), "APPROVED", null);
        var zone = com.dogsout.server.WeeklySeries.ZONE;
        var firstTue = java.time.LocalDate.now(zone).with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.TUESDAY))
                .atTime(13, 0).atZone(zone);
        var course = schools.createEvent(owner.getEmail(), new EventRequest("Junghundekurs", null,
                firstTue.toInstant(), firstTue.plusHours(1).toInstant(), null, null, null, null, 25000, 8,
                java.util.List.of("TUESDAY", "FRIDAY"), firstTue.toLocalDate().plusWeeks(6).plusDays(3), "COURSE"));

        assertThat(course.repeatWeekdays()).containsExactly("TUESDAY", "FRIDAY");
        assertThat(course.occurrenceMinutes()).isEqualTo(60);
        assertThat(course.nextStartsAt()).isEqualTo(firstTue.toInstant());
        // Ends with the last class: the Friday six weeks on.
        assertThat(course.endsAt().atZone(zone).getDayOfWeek()).isEqualTo(java.time.DayOfWeek.FRIDAY);
        assertThat(schools.upcomingNearby(lea.getEmail())).hasSize(1);
        assertThat(schools.join(lea.getEmail(), course.id()).joined()).isTrue();
    }

    private SchoolDtos.EventResponse weekly(User owner, String mode, Integer places) {
        var mine = schools.register(owner.getEmail(), school());
        schools.review(mine.id(), "APPROVED", null);
        var zone = com.dogsout.server.WeeklySeries.ZONE;
        var tue = java.time.LocalDate.now(zone).with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.TUESDAY))
                .atTime(13, 0).atZone(zone);
        return schools.createEvent(owner.getEmail(), new EventRequest("Social walk", null, tue.toInstant(),
                tue.plusHours(1).toInstant(), null, null, null, null, null, places,
                java.util.List.of("TUESDAY", "FRIDAY"), tue.toLocalDate().plusWeeks(4), mode));
    }

    @Test
    void perDateSignUpHasPlacesPerClass() {
        User owner = user("Schule"), lea = user("Lea"), moritz = user("Moritz");
        var ev = weekly(owner, "DATE", 1);
        assertThat(ev.signupMode()).isEqualTo("DATE");
        assertThat(ev.occurrences()).hasSize(8);
        var first = ev.occurrences().get(0).date();
        var second = ev.occurrences().get(1).date();

        // A date is required.
        assertThatThrownBy(() -> schools.join(lea.getEmail(), ev.id(), null)).isInstanceOf(ResponseStatusException.class);
        var after = schools.join(lea.getEmail(), ev.id(), first);
        assertThat(after.occurrences().get(0).joined()).isTrue();
        assertThat(after.occurrences().get(0).full()).isTrue();
        // The first class is full, the second is not.
        assertThatThrownBy(() -> schools.join(moritz.getEmail(), ev.id(), first)).hasMessageContaining("full");
        assertThat(schools.join(moritz.getEmail(), ev.id(), second).occurrences().get(1).joined()).isTrue();
        // The second class is now full too: one place per class, not per course.
        assertThatThrownBy(() -> schools.join(lea.getEmail(), ev.id(), second)).hasMessageContaining("full");
        assertThat(schools.participantsOf(owner.getEmail(), ev.id())).extracting(SchoolDtos.ParticipantResponse::occurrenceDate)
                .containsExactly(first, second);
        // A date that is not a class is refused.
        assertThatThrownBy(() -> schools.join(lea.getEmail(), ev.id(), first.plusDays(1))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void onePersonCanPickSeveralClasses() {
        User owner = user("Schule"), lea = user("Lea");
        var ev = weekly(owner, "DATE", null);
        schools.join(lea.getEmail(), ev.id(), ev.occurrences().get(0).date());
        var after = schools.join(lea.getEmail(), ev.id(), ev.occurrences().get(2).date());
        assertThat(after.occurrences()).extracting(SchoolDtos.OccurrenceResponse::joined)
                .startsWith(true, false, true);
        // Leaving one class keeps the other.
        var left = schools.leave(lea.getEmail(), ev.id(), ev.occurrences().get(0).date());
        assertThat(left.occurrences().get(0).joined()).isFalse();
        assertThat(left.joined()).isTrue();
    }

    @Test
    void cancellingOneClassDropsItAndItsSignUps() {
        User owner = user("Schule"), lea = user("Lea");
        var ev = weekly(owner, "DATE", null);
        var first = ev.occurrences().get(0).date();
        schools.join(lea.getEmail(), ev.id(), first);
        var after = schools.cancelDate(owner.getEmail(), ev.id(), first);
        assertThat(after.occurrences()).extracting(SchoolDtos.OccurrenceResponse::date).doesNotContain(first);
        assertThat(schools.participantsOf(owner.getEmail(), ev.id())).isEmpty();
    }

    @Test
    void aCourseIsOneSignUpForAllRemainingClasses() {
        User owner = user("Schule"), lea = user("Lea");
        var ev = weekly(owner, "COURSE", null);
        assertThat(ev.remainingClasses()).isGreaterThan(5);
        assertThat(schools.join(lea.getEmail(), ev.id()).joined()).isTrue();
        assertThat(schools.participantsOf(owner.getEmail(), ev.id())).singleElement()
                .extracting(SchoolDtos.ParticipantResponse::occurrenceDate).isNull();
        // The kind of sign-up is fixed once people have signed up.
        var r = new EventRequest("Social walk", null, ev.startsAt(), ev.startsAt().plusSeconds(3600), null, null, null, null,
                null, null, java.util.List.of("TUESDAY", "FRIDAY"), ev.endsAt().atZone(com.dogsout.server.WeeklySeries.ZONE).toLocalDate(), "DATE");
        assertThatThrownBy(() -> schools.updateEvent(owner.getEmail(), ev.id(), r)).hasMessageContaining("signed up");
    }
}
