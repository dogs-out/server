package com.dogsout.server.notification;

import com.dogsout.server.dog.Dog;
import com.dogsout.server.dog.DogRepository;
import com.dogsout.server.matching.Match;
import com.dogsout.server.matching.MatchRepository;
import com.dogsout.server.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The greeting exists to reopen a quiet conversation, so what matters is that it
 * reaches the matches — not the owner, who already knows — and that it carries
 * enough to land in the right chat.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BirthdayServiceTest {

    @Mock private DogRepository dogRepository;
    @Mock private com.dogsout.server.user.UserRepository userRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private PushNotificationService pushNotificationService;

    @InjectMocks private BirthdayService service;

    private static User user(long id, String name) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        return u;
    }

    private static Match match(long id, User a, User b) {
        Match m = new Match();
        m.setId(id);
        m.setUser1(a);
        m.setUser2(b);
        return m;
    }

    private Dog birthdayDog(User owner, LocalDate born) {
        Dog dog = new Dog();
        dog.setId(9L);
        dog.setName("Luna");
        dog.setDateOfBirth(born);
        dog.setOwner(owner);
        when(dogRepository.findBirthdaysOn(anyInt(), anyInt(), anyInt())).thenReturn(List.of(dog));
        when(userRepository.findBirthdaysOn(anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        return dog;
    }

    @Test
    void greetsEveryMatchOfTheOwnerButNotTheOwner() {
        User owner = user(1L, "Moritz");
        User lena = user(2L, "Lena");
        User jonas = user(3L, "Jonas");
        birthdayDog(owner, LocalDate.now().minusYears(5));
        // The owner sits on either side of a match row depending on who swiped first.
        when(matchRepository.findAllMatchesForUser(1L))
                .thenReturn(List.of(match(10L, owner, lena), match(11L, jonas, owner)));

        service.greetTodaysBirthdays();

        ArgumentCaptor<User> notified = ArgumentCaptor.forClass(User.class);
        // Both matches, plus the owner — theirs is a greeting rather than a prompt.
        verify(pushNotificationService, times(3)).send(notified.capture(), anyString(), anyString(), any());
        assertThat(notified.getAllValues()).extracting(User::getName)
                .containsExactlyInAnyOrder("Lena", "Jonas", "Moritz");
    }

    @Test
    void greetsAPersonsOwnBirthdayToTheirMatches() {
        User birthdayPerson = user(1L, "Moritz");
        birthdayPerson.setDateOfBirth(LocalDate.now().minusYears(30));
        when(dogRepository.findBirthdaysOn(anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        when(userRepository.findBirthdaysOn(anyInt(), anyInt(), anyInt())).thenReturn(List.of(birthdayPerson));
        when(matchRepository.findAllMatchesForUser(1L))
                .thenReturn(List.of(match(10L, birthdayPerson, user(2L, "Lena"))));

        service.greetTodaysBirthdays();

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<User> notified = ArgumentCaptor.forClass(User.class);
        verify(pushNotificationService).send(notified.capture(), title.capture(), anyString(), any());
        assertThat(notified.getValue().getName()).isEqualTo("Lena");
        assertThat(title.getValue()).isEqualTo("Moritz turns 30 today 🎂");
        assertThat(birthdayPerson.getBirthdayGreetedYear()).isEqualTo(LocalDate.now().getYear());
    }

    @Test
    void namesTheDogAndItsAge() {
        User owner = user(1L, "Moritz");
        birthdayDog(owner, LocalDate.now().minusYears(5));
        when(matchRepository.findAllMatchesForUser(1L)).thenReturn(List.of(match(10L, owner, user(2L, "Lena"))));

        service.greetTodaysBirthdays();

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(pushNotificationService, times(2)).send(any(), title.capture(), anyString(), any());
        assertThat(title.getAllValues()).containsOnly("Luna turns 5 today 🎂");
    }

    @Test
    void carriesEnoughToOpenTheRightChat() {
        User owner = user(1L, "Moritz");
        birthdayDog(owner, LocalDate.now().minusYears(3));
        when(matchRepository.findAllMatchesForUser(1L)).thenReturn(List.of(match(42L, owner, user(2L, "Lena"))));

        service.greetTodaysBirthdays();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(pushNotificationService, times(2)).send(any(), anyString(), anyString(), data.capture());
        assertThat(data.getAllValues().get(0))
                .containsEntry("type", "DOG_BIRTHDAY")
                .containsEntry("matchId", 42L)
                .containsEntry("otherUserId", 1L);
    }

    @Test
    void marksTheYearSoARestartCannotGreetTwice() {
        User owner = user(1L, "Moritz");
        Dog dog = birthdayDog(owner, LocalDate.now().minusYears(2));
        when(matchRepository.findAllMatchesForUser(1L)).thenReturn(List.of());

        service.greetTodaysBirthdays();

        assertThat(dog.getBirthdayGreetedYear()).isEqualTo(LocalDate.now().getYear());
        verify(dogRepository).save(dog);
    }

    @Test
    void saysNothingWhenNoDogHasABirthday() {
        when(dogRepository.findBirthdaysOn(anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        when(userRepository.findBirthdaysOn(anyInt(), anyInt(), anyInt())).thenReturn(List.of());

        service.greetTodaysBirthdays();

        verify(pushNotificationService, never()).send(any(), anyString(), anyString(), any());
    }
}
