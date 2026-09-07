package com.dogsout.server.playdate;

import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The reminder job's two jobs: notify the right people, and never notify them
 * twice. The windows themselves live in the derived query names; what is worth
 * pinning here is who ends up on the list and that the send is stamped.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlaydateReminderServiceTest {

    @Mock private PlaydateRepository playdateRepository;
    @Mock private PlaydateParticipantRepository participantRepository;
    @Mock private PushNotificationService pushNotificationService;

    @InjectMocks private PlaydateReminderService service;

    private static User user(long id, String name) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        return u;
    }

    private static PlaydateParticipant participant(Playdate playdate, User user, ParticipantStatus status) {
        PlaydateParticipant p = new PlaydateParticipant();
        p.setPlaydate(playdate);
        p.setUser(user);
        p.setStatus(status);
        return p;
    }

    private Playdate playdate(String title, Instant startsAt) {
        Playdate p = new Playdate();
        p.setId(7L);
        p.setTitle(title);
        p.setParkName("Cupertino Memorial Park");
        p.setStartsAt(startsAt);
        p.setStatus(PlaydateStatus.ACTIVE);
        p.setHost(user(1L, "Maya"));
        return p;
    }

    private void givenDayWindow(Playdate p) {
        when(playdateRepository.findByStatusAndDayReminderSentAtIsNullAndStartsAtAfterAndStartsAtBefore(
                any(), any(), any())).thenReturn(List.of(p));
        when(playdateRepository.findByStatusAndHourReminderSentAtIsNullAndStartsAtAfterAndStartsAtBefore(
                any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void remindsTheHostAndEveryoneWhoJoined() {
        Playdate p = playdate("Morning zoomies", Instant.now().plusSeconds(12 * 3600));
        givenDayWindow(p);
        when(participantRepository.findByPlaydate(p)).thenReturn(List.of(
                participant(p, user(2L, "Chris"), ParticipantStatus.JOINED),
                participant(p, user(3L, "Sarah"), ParticipantStatus.JOINED),
                // Invited but never accepted — not going, so not reminded.
                participant(p, user(4L, "Leo"), ParticipantStatus.INVITED)));

        service.sendDueReminders();

        ArgumentCaptor<User> notified = ArgumentCaptor.forClass(User.class);
        verify(pushNotificationService, times(3)).send(notified.capture(), anyString(), anyString(), any());
        assertThat(notified.getAllValues()).extracting(User::getName)
                .containsExactlyInAnyOrder("Maya", "Chris", "Sarah");
    }

    @Test
    void stampsTheDayReminderSoItCannotRepeat() {
        Playdate p = playdate("Morning zoomies", Instant.now().plusSeconds(12 * 3600));
        givenDayWindow(p);
        when(participantRepository.findByPlaydate(p)).thenReturn(List.of());

        service.sendDueReminders();

        assertThat(p.getDayReminderSentAt()).isNotNull();
        assertThat(p.getHourReminderSentAt()).isNull();
        verify(playdateRepository).save(p);
    }

    @Test
    void aPlaydateInsideTheHourAlsoRetiresItsDayReminder() {
        // Created less than an hour before it starts: "tomorrow" would be a lie, so
        // only the hour reminder goes out and the day one is closed off.
        Playdate p = playdate("Sunset walk", Instant.now().plusSeconds(30 * 60));
        when(playdateRepository.findByStatusAndDayReminderSentAtIsNullAndStartsAtAfterAndStartsAtBefore(
                any(), any(), any())).thenReturn(List.of());
        when(playdateRepository.findByStatusAndHourReminderSentAtIsNullAndStartsAtAfterAndStartsAtBefore(
                any(), any(), any())).thenReturn(List.of(p));
        when(participantRepository.findByPlaydate(p)).thenReturn(List.of());

        service.sendDueReminders();

        assertThat(p.getHourReminderSentAt()).isNotNull();
        assertThat(p.getDayReminderSentAt()).isNotNull();
    }

    @Test
    void carriesTheDeepLinkPayload() {
        Playdate p = playdate(null, Instant.now().plusSeconds(12 * 3600));   // untitled
        givenDayWindow(p);
        when(participantRepository.findByPlaydate(p)).thenReturn(List.of());

        service.sendDueReminders();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(pushNotificationService).send(any(), anyString(), body.capture(), data.capture());

        // An untitled playdate is known by its park, same as everywhere else.
        assertThat(body.getValue()).contains("Cupertino Memorial Park");
        assertThat(data.getValue()).containsEntry("type", "PLAYDATE_REMINDER").containsEntry("playdateId", 7L);
    }

    @Test
    void doesNothingWhenNothingIsDue() {
        when(playdateRepository.findByStatusAndDayReminderSentAtIsNullAndStartsAtAfterAndStartsAtBefore(
                any(), any(), any())).thenReturn(List.of());
        when(playdateRepository.findByStatusAndHourReminderSentAtIsNullAndStartsAtAfterAndStartsAtBefore(
                any(), any(), any())).thenReturn(List.of());

        service.sendDueReminders();

        verify(pushNotificationService, never()).send(any(), anyString(), anyString(), any());
    }
}
