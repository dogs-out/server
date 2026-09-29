package com.dogsout.server.playdate;

import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.playdate.PlaydateDtos.WalkInviteRequest;
import com.dogsout.server.user.User;
import com.dogsout.server.websocket.ChatSocketHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A walk invited from a chat: a two-person, invite-only playdate with the partner already invited. */
@ExtendWith(MockitoExtension.class)
class WalkInviteTest {

    @Mock PlaydateRepository playdateRepository;
    @Mock PlaydateParticipantRepository participantRepository;
    @Mock com.dogsout.server.ProfanityFilter profanityFilter;
    @Mock ChatSocketHandler chatSocketHandler;
    @Mock PushNotificationService pushNotificationService;

    private PlaydateService service() {
        return new PlaydateService(playdateRepository, participantRepository, null, null, null, null,
                profanityFilter, chatSocketHandler, pushNotificationService, null);
    }

    private static User user(long id, Boolean hasDog) {
        User u = new User();
        u.setId(id);
        u.setHasDog(hasDog);
        return u;
    }

    private static WalkInviteRequest request(Instant startsAt) {
        return new WalkInviteRequest("🐾 Walk invite", "Irchelpark", "Zürich", 47.397, 8.545, startsAt, null);
    }

    @Test
    void createsATwoPersonInviteOnlyWalkWithThePartnerInvited() {
        when(playdateRepository.save(any())).thenAnswer(inv -> {
            Playdate p = inv.getArgument(0);
            p.setId(7L);
            return p;
        });

        Playdate walk = service().createWalk(user(1, true), user(2, true),
                request(Instant.now().plus(1, ChronoUnit.DAYS)));

        assertThat(walk.getWalk()).isTrue();
        assertThat(walk.getVisibility()).isEqualTo(PlaydateVisibility.INVITE_ONLY);
        assertThat(walk.getMaxParticipants()).isEqualTo(2);
        assertThat(walk.getParkName()).isEqualTo("Irchelpark");

        ArgumentCaptor<PlaydateParticipant> invited = ArgumentCaptor.forClass(PlaydateParticipant.class);
        verify(participantRepository).save(invited.capture());
        assertThat(invited.getValue().getUser().getId()).isEqualTo(2L);
        assertThat(invited.getValue().getStatus()).isEqualTo(ParticipantStatus.INVITED);
        // The chat message is the invite's one push; a second playdate push would double it.
        verify(pushNotificationService, never()).send(any(), any(), any(), any());
    }

    @Test
    void walksAreHostedByDogOwners() {
        assertThatThrownBy(() -> service().createWalk(user(1, false), user(2, true),
                request(Instant.now().plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(playdateRepository);
    }

    @Test
    void aWalkCannotStartInThePast() {
        assertThatThrownBy(() -> service().createWalk(user(1, true), user(2, true),
                request(Instant.now().minus(1, ChronoUnit.HOURS))))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(playdateRepository);
    }
}
