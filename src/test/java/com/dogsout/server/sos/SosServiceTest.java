package com.dogsout.server.sos;

import com.dogsout.server.ProfanityFilter;
import com.dogsout.server.dog.Dog;
import com.dogsout.server.dog.DogRepository;
import com.dogsout.server.dog.DogService;
import com.dogsout.server.moderation.BlockRepository;
import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.photo.PhotoService;
import com.dogsout.server.sos.SosDtos.RaiseAlertRequest;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SosServiceTest {

    @Mock LostDogAlertRepository alertRepository;
    @Mock UserRepository userRepository;
    @Mock DogRepository dogRepository;
    @Mock DogService dogService;
    @Mock BlockRepository blockRepository;
    @Mock ProfanityFilter profanityFilter;
    @Mock PhotoService photoService;
    @Mock PushNotificationService push;

    SosService service;
    User owner;
    Dog dog;

    // Zürich HB, and points at known distances from it.
    static final double ZH_LAT = 47.3779, ZH_LNG = 8.5403;

    static User user(long id, Double lat, Double lng) {
        User u = new User();
        u.setId(id);
        u.setEmail("u" + id + "@test");
        u.setName("User" + id);
        u.setLatitude(lat);
        u.setLongitude(lng);
        return u;
    }

    @BeforeEach
    void setUp() {
        service = new SosService(alertRepository, userRepository, dogRepository, dogService, null,
                blockRepository, profanityFilter, photoService, push, null);
        owner = user(1, ZH_LAT, ZH_LNG);
        dog = new Dog();
        dog.setId(10L);
        dog.setName("Luna");
        dog.setOwner(owner);
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(dogRepository.findById(10L)).thenReturn(Optional.of(dog));
        when(alertRepository.save(any())).thenAnswer(inv -> {
            LostDogAlert a = inv.getArgument(0);
            a.setId(99L);
            a.setCreatedAt(Instant.now());
            return a;
        });
        when(push.canReceive(any())).thenReturn(true);
    }

    private RaiseAlertRequest request() {
        return new RaiseAlertRequest(10L, ZH_LAT, ZH_LNG, "Irchelpark", null);
    }

    @Test
    void pushesEveryoneWithinAHundredKilometresButNotTheOwnerOrTheBlocked() {
        User winterthur = user(2, 47.4988, 8.7237);   // ~19 km
        User bern = user(3, 46.9480, 7.4474);         // ~95 km
        User geneva = user(4, 46.2044, 6.1432);       // ~225 km
        User noLocation = user(5, null, null);
        User blockedNearby = user(6, 47.40, 8.55);
        when(userRepository.findAll()).thenReturn(List.of(owner, winterthur, bern, geneva, noLocation, blockedNearby));
        when(blockRepository.existsBlockBetween(1L, 6L)).thenReturn(true);

        service.raise(owner.getEmail(), request());

        verify(push).send(eq(winterthur), any(), any(), any());
        verify(push).send(eq(bern), any(), any(), any());
        verify(push, never()).send(eq(geneva), any(), any(), any());
        verify(push, never()).send(eq(noLocation), any(), any(), any());
        verify(push, never()).send(eq(blockedNearby), any(), any(), any());
        verify(push, never()).send(eq(owner), any(), any(), any());
    }

    @Test
    void anOwnerHasOneOpenAlertAtATime() {
        when(alertRepository.findByOwnerAndClosedAtIsNullAndCreatedAtAfter(eq(owner), any()))
                .thenReturn(List.of(new LostDogAlert()));

        assertThatThrownBy(() -> service.raise(owner.getEmail(), request()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(push, never()).send(any(), any(), any(), any());
    }

    @Test
    void raisingIsCappedPerWeek() {
        when(alertRepository.countByOwnerAndCreatedAtAfter(eq(owner), any())).thenReturn((long) SosService.MAX_PER_WEEK);

        assertThatThrownBy(() -> service.raise(owner.getEmail(), request()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void onlyTheOwnerCanRaiseAnAlertForADog() {
        User stranger = user(7, ZH_LAT, ZH_LNG);
        when(userRepository.findByEmail(stranger.getEmail())).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> service.raise(stranger.getEmail(), request()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void anAlertClosesItselfAfterAWeek() {
        LostDogAlert alert = new LostDogAlert();
        alert.setCreatedAt(Instant.now().minus(java.time.Duration.ofDays(SosService.OPEN_DAYS + 1)));
        assertThat(alert.isOpen(Instant.now())).isFalse();
        alert.setCreatedAt(Instant.now().minus(java.time.Duration.ofDays(1)));
        assertThat(alert.isOpen(Instant.now())).isTrue();
        alert.setClosedAt(Instant.now());
        assertThat(alert.isOpen(Instant.now())).isFalse();
    }

    @Test
    void closingAsFoundTellsTheSameArea() {
        LostDogAlert alert = new LostDogAlert();
        alert.setId(99L);
        alert.setOwner(owner);
        alert.setDog(dog);
        alert.setLatitude(ZH_LAT);
        alert.setLongitude(ZH_LNG);
        alert.setCreatedAt(Instant.now());
        when(alertRepository.findById(anyLong())).thenReturn(Optional.of(alert));
        User nearby = user(2, 47.4988, 8.7237);
        when(userRepository.findAll()).thenReturn(List.of(owner, nearby));

        service.close(owner.getEmail(), 99L, true);

        assertThat(alert.getClosedAt()).isNotNull();
        assertThat(alert.getFound()).isTrue();
        verify(push).send(eq(nearby), any(), any(), any());
    }
}
