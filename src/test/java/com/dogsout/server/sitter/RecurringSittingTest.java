package com.dogsout.server.sitter;

import com.dogsout.server.dog.Dog;
import com.dogsout.server.dog.DogRepository;
import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sittings that repeat every week, and ones that last several days. */
@SpringBootTest
@Transactional
class RecurringSittingTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    @Autowired SitterService sitters;
    @Autowired UserRepository users;
    @Autowired DogRepository dogs;
    @MockitoBean PushNotificationService push;

    private User owner;
    private Dog luna;

    private void setUpOwner() {
        owner = new User();
        owner.setEmail("owner@recurring.test");
        owner.setName("Owner");
        owner.setPassword("x");
        owner.setDateOfBirth(LocalDate.of(1990, 1, 1));
        owner = users.save(owner);
        luna = new Dog();
        luna.setName("Luna");
        luna.setOwner(owner);
        luna = dogs.save(luna);
    }

    /** Next Tuesday at 09:00 in Zurich. */
    private java.time.ZonedDateTime nextTuesdayNine() {
        return LocalDate.now(ZURICH).with(TemporalAdjusters.next(DayOfWeek.TUESDAY)).atTime(LocalTime.of(9, 0)).atZone(ZURICH);
    }

    @Test
    void aWeeklySeriesEndsWithItsLastVisit() {
        setUpOwner();
        var first = nextTuesdayNine();
        LocalDate until = first.toLocalDate().plusWeeks(4).plusDays(1); // a Wednesday
        var created = sitters.createRequest(owner.getEmail(), new CreateSittingRequest(
                first.toInstant(), first.plusHours(3).toInstant(), List.of(luna.getId()), null,
                List.of("TUESDAY", "THURSDAY"), until));

        assertThat(created.repeatWeekdays()).containsExactly("TUESDAY", "THURSDAY");
        assertThat(created.occurrenceMinutes()).isEqualTo(180);
        // The last visit is the Tuesday four weeks on, since the series stops on a Wednesday.
        var lastEnd = first.plusWeeks(4).plusHours(3);
        assertThat(created.endsAt()).isEqualTo(lastEnd.toInstant());
    }

    @Test
    void theFirstVisitMustBeOneOfTheWeekdays() {
        setUpOwner();
        var first = nextTuesdayNine();
        assertThatThrownBy(() -> sitters.createRequest(owner.getEmail(), new CreateSittingRequest(
                first.toInstant(), first.plusHours(2).toInstant(), List.of(luna.getId()), null,
                List.of("FRIDAY"), first.toLocalDate().plusWeeks(2))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void aHolidaySittingCanLastDaysButNotForever() {
        setUpOwner();
        var start = nextTuesdayNine();
        var week = sitters.createRequest(owner.getEmail(), new CreateSittingRequest(
                start.toInstant(), start.plusDays(7).toInstant(), List.of(luna.getId()), "Ferien"));
        assertThat(Duration.between(week.startsAt(), week.endsAt()).toDays()).isEqualTo(7);
        assertThat(week.repeatWeekdays()).isEmpty();

        assertThatThrownBy(() -> sitters.createRequest(owner.getEmail(), new CreateSittingRequest(
                start.toInstant(), start.plusDays(45).toInstant(), List.of(luna.getId()), null)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
