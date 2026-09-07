package com.dogsout.server.playdate;

import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reminds everyone going to a playdate that it is coming up — a day before, and
 * again an hour before.
 *
 * <p>Both reminders are stamped on the playdate as they go out, so a redeploy
 * mid-window or two overlapping runs cannot notify the same people twice. A
 * playdate created less than a day ahead never receives the day reminder, which
 * is the intended behaviour rather than a gap: telling someone "tomorrow" about
 * something starting in three hours is worse than staying quiet.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PlaydateReminderService {

    /**
     * How often the windows are checked. Five minutes is close enough that a
     * reminder lands within a few minutes of its mark, and cheap enough that it
     * costs one indexed query per window.
     */
    private static final long CHECK_INTERVAL_MS = 5 * 60 * 1000L;

    private static final Duration DAY_BEFORE = Duration.ofHours(24);
    private static final Duration HOUR_BEFORE = Duration.ofHours(1);

    private final PlaydateRepository playdateRepository;
    private final PlaydateParticipantRepository participantRepository;
    private final PushNotificationService pushNotificationService;

    @Scheduled(fixedDelay = CHECK_INTERVAL_MS, initialDelay = 60_000)
    @Transactional
    public void sendDueReminders() {
        Instant now = Instant.now();

        // Day window: starting within 24h but not yet inside the hour window, so the
        // two reminders can never fire together for the same playdate.
        for (Playdate playdate : playdateRepository
                .findByStatusAndDayReminderSentAtIsNullAndStartsAtAfterAndStartsAtBefore(
                        PlaydateStatus.ACTIVE, now.plus(HOUR_BEFORE), now.plus(DAY_BEFORE))) {
            notify(playdate, "Playdate tomorrow 🐾",
                    "\"" + displayName(playdate) + "\" at " + playdate.getParkName() + " is coming up.");
            playdate.setDayReminderSentAt(now);
            playdateRepository.save(playdate);
        }

        for (Playdate playdate : playdateRepository
                .findByStatusAndHourReminderSentAtIsNullAndStartsAtAfterAndStartsAtBefore(
                        PlaydateStatus.ACTIVE, now, now.plus(HOUR_BEFORE))) {
            notify(playdate, "Playdate in an hour 🐾",
                    "\"" + displayName(playdate) + "\" at " + playdate.getParkName() + " starts soon.");
            playdate.setHourReminderSentAt(now);
            // A playdate this close never wants the day reminder afterwards either.
            if (playdate.getDayReminderSentAt() == null) playdate.setDayReminderSentAt(now);
            playdateRepository.save(playdate);
        }
    }

    /** The host counts as an attendee; everyone else has to have actually joined. */
    private void notify(Playdate playdate, String title, String body) {
        Set<User> recipients = new LinkedHashSet<>();
        recipients.add(playdate.getHost());
        participantRepository.findByPlaydate(playdate).stream()
                .filter(p -> p.getStatus() == ParticipantStatus.JOINED)
                .map(PlaydateParticipant::getUser)
                .forEach(recipients::add);

        Map<String, Object> data = Map.of("type", "PLAYDATE_REMINDER", "playdateId", playdate.getId());
        recipients.forEach(user -> pushNotificationService.send(user, title, body, data));
        log.info("Playdate {} reminder sent to {} attendee(s)", playdate.getId(), recipients.size());
    }

    /** Mirrors PlaydateService: an untitled playdate is known by its park. */
    private String displayName(Playdate playdate) {
        String title = playdate.getTitle();
        return title == null || title.isBlank() ? playdate.getParkName() : title;
    }

    /** Exposed for tests, which need to drive the clock rather than wait for it. */
    List<Playdate> dueForDayReminder(Instant now) {
        return playdateRepository.findByStatusAndDayReminderSentAtIsNullAndStartsAtAfterAndStartsAtBefore(
                PlaydateStatus.ACTIVE, now.plus(HOUR_BEFORE), now.plus(DAY_BEFORE));
    }
}
