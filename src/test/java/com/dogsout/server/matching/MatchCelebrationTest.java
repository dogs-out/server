package com.dogsout.server.matching;

import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The second swiper sees the match screen straight away; the first liker is
 * offered it later, exactly once.
 */
@SpringBootTest
@Transactional
class MatchCelebrationTest {

    @Autowired MatchService matchService;
    @Autowired UserRepository userRepository;
    @MockitoBean PushNotificationService push;

    User user(String name) {
        User u = new User();
        u.setEmail(name.toLowerCase() + "@test");
        u.setName(name);
        u.setPassword("x");
        u.setDateOfBirth(LocalDate.of(1990, 1, 1));
        return userRepository.save(u);
    }

    @Test
    void firstLikerIsShownTheMatchOnceAndTheSecondSwiperNever() {
        User lea = user("Lea");
        User philip = user("Philip");

        assertThat(matchService.swipe(lea.getEmail(), new SwipeRequest(philip.getId(), "LIKE")).match()).isFalse();
        SwipeResponse second = matchService.swipe(philip.getEmail(), new SwipeRequest(lea.getId(), "LIKE"));
        assertThat(second.match()).isTrue();

        assertThat(matchService.getUncelebrated(philip.getEmail())).isEmpty();
        assertThat(matchService.getUncelebrated(lea.getEmail()))
                .singleElement()
                .satisfies(m -> assertThat(m.otherUserName()).isEqualTo("Philip"));

        // Only Lea can clear her own flag.
        matchService.markCelebrated(philip.getEmail(), second.matchId());
        assertThat(matchService.getUncelebrated(lea.getEmail())).hasSize(1);

        matchService.markCelebrated(lea.getEmail(), second.matchId());
        assertThat(matchService.getUncelebrated(lea.getEmail())).isEmpty();
    }
}
