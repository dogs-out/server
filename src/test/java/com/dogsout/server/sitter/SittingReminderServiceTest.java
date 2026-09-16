package com.dogsout.server.sitter;

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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rating nudge has to reach the owner exactly once. Asking twice for the
 * same sitting is worse than not asking: it reads as a bug and it is the sort
 * of thing that gets notifications switched off wholesale.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SittingReminderServiceTest {

    @Mock private SittingRequestRepository sittingRequestRepository;
    @Mock private SitterReviewRepository sitterReviewRepository;
    @Mock private PushNotificationService pushNotificationService;

    @InjectMocks private SittingReminderService service;

    private static User user(long id, String name) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        return u;
    }

    private static SittingRequest finished(long id, User owner, User sitter) {
        SittingRequest r = new SittingRequest();
        r.setId(id);
        r.setOwner(owner);
        r.setSitter(sitter);
        r.setStartsAt(Instant.now().minus(6, ChronoUnit.HOURS));
        r.setEndsAt(Instant.now().minus(2, ChronoUnit.HOURS));
        r.setStatus(SittingRequestStatus.CLOSED);
        return r;
    }

    @Test
    void asksTheOwnerToRateTheSitter() {
        User owner = user(1, "Mara");
        User sitter = user(2, "Jonas");
        SittingRequest job = finished(10, owner, sitter);
        when(sittingRequestRepository
                .findBySitterIsNotNullAndEndsAtBeforeAndRatingReminderSentAtIsNull(any()))
                .thenReturn(List.of(job));
        when(sitterReviewRepository.existsByRequest(job)).thenReturn(false);

        service.askForRatings();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(pushNotificationService).send(any(), anyString(), anyString(), data.capture());
        assertThat(data.getValue()).containsEntry("type", "SITTING_RATE");
        assertThat(data.getValue()).containsEntry("requestId", 10L);
        assertThat(data.getValue()).containsEntry("otherUserId", 2L);
    }

    @Test
    void stampsTheRequestSoItIsNeverAskedTwice() {
        SittingRequest job = finished(11, user(1, "Mara"), user(2, "Jonas"));
        when(sittingRequestRepository
                .findBySitterIsNotNullAndEndsAtBeforeAndRatingReminderSentAtIsNull(any()))
                .thenReturn(List.of(job));

        service.askForRatings();

        assertThat(job.getRatingReminderSentAt()).isNotNull();
    }

    @Test
    void doesNotAskWhenTheOwnerAlreadyRatedInTheApp() {
        SittingRequest job = finished(12, user(1, "Mara"), user(2, "Jonas"));
        when(sittingRequestRepository
                .findBySitterIsNotNullAndEndsAtBeforeAndRatingReminderSentAtIsNull(any()))
                .thenReturn(List.of(job));
        when(sitterReviewRepository.existsByRequest(job)).thenReturn(true);

        service.askForRatings();

        verify(pushNotificationService, never()).send(any(), anyString(), anyString(), any());
        // Still stamped, or the job is reconsidered on every single pass forever.
        assertThat(job.getRatingReminderSentAt()).isNotNull();
    }

    @Test
    void sendsNothingWhenNoSittingHasFinished() {
        when(sittingRequestRepository
                .findBySitterIsNotNullAndEndsAtBeforeAndRatingReminderSentAtIsNull(any()))
                .thenReturn(List.of());

        service.askForRatings();

        verify(pushNotificationService, never()).send(any(), anyString(), anyString(), any());
    }
}
