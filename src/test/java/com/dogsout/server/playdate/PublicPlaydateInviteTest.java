package com.dogsout.server.playdate;

import com.dogsout.server.ProfanityFilter;
import com.dogsout.server.matching.Match;
import com.dogsout.server.matching.MatchRepository;
import com.dogsout.server.moderation.BlockRepository;
import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.photo.PhotoService;
import com.dogsout.server.playdate.PlaydateDtos.CreatePlaydateRequest;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import com.dogsout.server.websocket.ChatSocketHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A public playdate can still invite friends directly: they get the push and the
 * invite row, while everyone nearby can join on their own as before.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicPlaydateInviteTest {

    @Mock PlaydateRepository playdateRepository;
    @Mock PlaydateParticipantRepository participantRepository;
    @Mock PlaydateMessageRepository messageRepository;
    @Mock UserRepository userRepository;
    @Mock MatchRepository matchRepository;
    @Mock BlockRepository blockRepository;
    @Mock ProfanityFilter profanityFilter;
    @Mock ChatSocketHandler chatSocketHandler;
    @Mock PushNotificationService pushNotificationService;
    @Mock PhotoService photoService;

    private PlaydateService service() {
        return new PlaydateService(playdateRepository, participantRepository, messageRepository, userRepository,
                matchRepository, blockRepository, profanityFilter, chatSocketHandler, pushNotificationService,
                photoService);
    }

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        u.setName("User " + id);
        u.setEmail("u" + id + "@test");
        u.setHasDog(true);
        return u;
    }

    private static Match match(User a, User b) {
        Match m = new Match();
        m.setUser1(a);
        m.setUser2(b);
        return m;
    }

    @Test
    void aPublicPlaydateInvitesTheChosenMatches() {
        User host = user(1), friend = user(2);
        when(userRepository.findByEmail("u1@test")).thenReturn(Optional.of(host));
        when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
        when(matchRepository.findAllMatchesForUser(1L)).thenReturn(List.of(match(host, friend)));
        when(participantRepository.findByPlaydateAndUser(any(), eq(friend))).thenReturn(Optional.empty());
        when(playdateRepository.save(any())).thenAnswer(inv -> {
            Playdate p = inv.getArgument(0);
            p.setId(9L);
            return p;
        });

        service().create("u1@test", new CreatePlaydateRequest("Sunday walk", null, "Irchelpark", null,
                47.397, 8.545, Instant.now().plus(1, ChronoUnit.DAYS), null, true,
                PlaydateVisibility.PUBLIC, List.of(2L)));

        ArgumentCaptor<PlaydateParticipant> invited = ArgumentCaptor.forClass(PlaydateParticipant.class);
        verify(participantRepository).save(invited.capture());
        assertThat(invited.getValue().getUser()).isEqualTo(friend);
        assertThat(invited.getValue().getStatus()).isEqualTo(ParticipantStatus.INVITED);
        assertThat(invited.getValue().getPlaydate().getVisibility()).isEqualTo(PlaydateVisibility.PUBLIC);
        verify(pushNotificationService).send(eq(friend), anyString(), anyString(), any());
    }
}
