package com.dogsout.server.playdate;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PlaydateRepository extends JpaRepository<Playdate, Long> {

    List<Playdate> findByHost(User host);

    List<Playdate> findByStatusAndStartsAtAfter(PlaydateStatus status, Instant after);

    /**
     * Playdates due a reminder: still active, starting inside the window, and not
     * yet notified. `startsAt > notBefore` keeps the day reminder from firing for
     * something already inside the one-hour window, which would send both at once.
     */
    List<Playdate> findByStatusAndDayReminderSentAtIsNullAndStartsAtAfterAndStartsAtBefore(
            PlaydateStatus status, Instant notBefore, Instant notAfter);

    List<Playdate> findByStatusAndHourReminderSentAtIsNullAndStartsAtAfterAndStartsAtBefore(
            PlaydateStatus status, Instant notBefore, Instant notAfter);
}
