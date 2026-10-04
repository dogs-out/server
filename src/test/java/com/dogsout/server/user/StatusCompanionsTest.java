package com.dogsout.server.user;

import com.dogsout.server.dog.Dog;
import com.dogsout.server.dog.DogRepository;
import com.dogsout.server.matching.Match;
import com.dogsout.server.matching.MatchRepository;
import com.dogsout.server.moderation.BlockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/** Who a walking status may name as company: matches only, each with a dog of their own. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatusCompanionsTest {

    @Mock MatchRepository matchRepository;
    @Mock BlockRepository blockRepository;
    @Mock DogRepository dogRepository;
    @InjectMocks UserService service;

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private static Dog dog(long id, User owner) {
        Dog d = new Dog();
        d.setId(id);
        d.setOwner(owner);
        return d;
    }

    private String pick(User me, List<StatusCompanion.Pick> picks) {
        return ReflectionTestUtils.invokeMethod(service, "companionsFor", me, picks);
    }

    @Test
    void keepsMatchesWithTheirOwnDogAndDropsEverythingElse() {
        User me = user(1), lea = user(2), stranger = user(3);
        Match match = new Match();
        match.setUser1(me);
        match.setUser2(lea);
        when(matchRepository.findAllMatchesForUser(1L)).thenReturn(List.of(match));
        when(blockRepository.existsBlockBetween(anyLong(), anyLong())).thenReturn(false);
        when(dogRepository.findById(20L)).thenReturn(Optional.of(dog(20, lea)));       // Lea's Maylie
        when(dogRepository.findById(30L)).thenReturn(Optional.of(dog(30, stranger)));  // not Lea's

        assertThat(pick(me, List.of(new StatusCompanion.Pick(2L, 20L)))).isEqualTo("2:20");
        // Someone else's dog under Lea's name is dropped, Lea stays
        assertThat(pick(me, List.of(new StatusCompanion.Pick(2L, 30L)))).isEqualTo("2:");
        // A stranger cannot be named at all
        assertThat(pick(me, List.of(new StatusCompanion.Pick(3L, 30L)))).isNull();
    }

    @Test
    void aBlockedMatchCannotBeNamed() {
        User me = user(1), lea = user(2);
        Match match = new Match();
        match.setUser1(lea);
        match.setUser2(me);
        when(matchRepository.findAllMatchesForUser(1L)).thenReturn(List.of(match));
        when(blockRepository.existsBlockBetween(1L, 2L)).thenReturn(true);

        assertThat(pick(me, List.of(new StatusCompanion.Pick(2L, null)))).isNull();
    }
}
