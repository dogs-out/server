package com.dogsout.server.sitter;

import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Asks the owner to rate the sitter once the sitting is over.
 *
 * <p>Nobody opens an app to leave a review unprompted, so a rating feature without
 * a nudge collects almost nothing — and a sitter with no ratings is exactly the
 * sitter people are nervous about booking. This is the one notification that makes
 * the rest of the feature work.
 *
 * <p>Hourly rather than daily: a sitting that ended this morning is still fresh
 * this afternoon, and asking two days later gets a vaguer answer. The stamp is
 * written before the push goes out, so a crash costs one reminder rather than
 * sending it twice.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SittingReminderService {

    /** Long enough that the handover is done, short enough that the day is fresh. */
    private static final long SETTLE_MINUTES = 30;

    private final SittingRequestRepository sittingRequestRepository;
    private final SitterReviewRepository sitterReviewRepository;
    private final PushNotificationService pushNotificationService;

    @Scheduled(cron = "0 5 * * * *", zone = "Europe/Zurich")
    @Transactional
    public void askForRatings() {
        Instant cutoff = Instant.now().minusSeconds(SETTLE_MINUTES * 60);
        List<SittingRequest> finished = sittingRequestRepository
                .findBySitterIsNotNullAndEndsAtBeforeAndRatingReminderSentAtIsNull(cutoff);

        for (SittingRequest job : finished) {
            // Somebody who already rated from the app itself never gets asked.
            if (sitterReviewRepository.existsByRequest(job)) {
                job.setRatingReminderSentAt(Instant.now());
                continue;
            }
            job.setRatingReminderSentAt(Instant.now());
            sittingRequestRepository.save(job);

            User owner = job.getOwner();
            User sitter = job.getSitter();
            pushNotificationService.send(owner,
                    "How did it go with " + sitter.getName() + "?",
                    "Rate the sitting so other owners know what to expect.",
                    Map.of("type", "SITTING_RATE",
                            "requestId", job.getId(),
                            "otherUserId", sitter.getId(),
                            "name", sitter.getName()));
        }
        if (!finished.isEmpty()) {
            log.info("Asked for {} sitting rating(s)", finished.size());
        }
    }
}
