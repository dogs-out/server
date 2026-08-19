package com.dogsout.server.user;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceAgeTest {

    private static LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    @Test
    void acceptsAnAdult() {
        assertThatCode(() -> UserService.requireAdult(today().minusYears(30)))
                .doesNotThrowAnyException();
    }

    /** The birthday itself counts — someone turning 18 today is 18. */
    @Test
    void acceptsSomeoneExactlyEighteenToday() {
        assertThatCode(() -> UserService.requireAdult(today().minusYears(18)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSomeoneOneDayShortOfEighteen() {
        LocalDate tomorrowsBirthday = today().minusYears(18).plusDays(1);

        assertThatThrownBy(() -> UserService.requireAdult(tomorrowsBirthday))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at least 18");
    }

    @Test
    void rejectsAChild() {
        assertThatThrownBy(() -> UserService.requireAdult(today().minusYears(12)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    /**
     * A future date would otherwise sail through the age comparison, and it is the
     * obvious thing to send when probing the check.
     */
    @Test
    void rejectsADateInTheFuture() {
        assertThatThrownBy(() -> UserService.requireAdult(today().plusDays(1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("future");
    }
}
