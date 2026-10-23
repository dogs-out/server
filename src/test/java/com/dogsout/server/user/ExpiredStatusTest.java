package com.dogsout.server.user;

import com.dogsout.server.dog.DogRepository;
import com.dogsout.server.matching.Match;
import com.dogsout.server.matching.MatchRepository;
import com.dogsout.server.photo.PhotoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A status that ran out must take its place and photo with it. The bug: Lea's walk
 * ended, and her matches still saw the park and the picture she had attached.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpiredStatusTest {

    @Mock UserRepository userRepository;
    @Mock MatchRepository matchRepository;
    @Mock PhotoService photoService;
    @Mock DogRepository dogRepository;
    @InjectMocks UserService service;

    private static User walkingUntil(long id, Instant until) {
        User u = new User();
        u.setId(id);
        u.setEmail("u" + id + "@test");
        u.setWalkStatus(WalkStatus.WALKING);
        u.setWalkStatusExpiresAt(until);
        u.setWalkStatusLatitude(47.39);
        u.setWalkStatusLongitude(8.54);
        u.setWalkStatusPlaceName("Irchelpark");
        u.setWalkStatusPhotoKey("users/2/status.jpg");
        return u;
    }

    private FriendStatus seenBy(User me, User lea) {
        Match match = new Match();
        match.setUser1(me);
        match.setUser2(lea);
        when(userRepository.findByEmail(me.getEmail())).thenReturn(Optional.of(me));
        when(matchRepository.findAllMatchesForUser(me.getId())).thenReturn(List.of(match));
        when(photoService.url(eq("users/2/status.jpg"), any())).thenReturn("https://cdn/status.jpg");
        return service.friendStatuses(me.getEmail()).get(0);
    }

    @Test
    void anExpiredStatusShowsNoPlaceOrPhoto() {
        User me = new User();
        me.setId(1L);
        me.setEmail("me@test");
        FriendStatus lea = seenBy(me, walkingUntil(2, Instant.now().minus(Duration.ofMinutes(5))));

        assertThat(lea.photo()).isNull();
        assertThat(lea.latitude()).isNull();
        assertThat(lea.placeName()).isNull();
    }

    @Test
    void aLiveStatusStillShowsThem() {
        User me = new User();
        me.setId(1L);
        me.setEmail("me@test");
        FriendStatus lea = seenBy(me, walkingUntil(2, Instant.now().plus(Duration.ofHours(1))));

        assertThat(lea.photo()).isEqualTo("https://cdn/status.jpg");
        assertThat(lea.placeName()).isEqualTo("Irchelpark");
    }

    @Test
    void theCleanupDeletesTheStoredPhoto() {
        User lea = walkingUntil(2, Instant.now().minus(Duration.ofMinutes(5)));
        when(userRepository.findByWalkStatusIsNotNullAndWalkStatusExpiresAtBefore(any())).thenReturn(List.of(lea));

        new StatusCleanupService(userRepository, photoService).clearExpired();

        assertThat(lea.getWalkStatus()).isNull();
        assertThat(lea.getWalkStatusPhotoKey()).isNull();
        assertThat(lea.getWalkStatusLatitude()).isNull();
        verify(photoService).delete("users/2/status.jpg");
    }
}
