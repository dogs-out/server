package com.dogsout.server.sitter;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SittingRequestRepository extends JpaRepository<SittingRequest, Long> {

    List<SittingRequest> findByOwnerOrderByStartsAtAsc(User owner);

    /**
     * Open jobs whose window has not yet closed, soonest first.
     *
     * <p>Keyed on the end rather than the start: a sitting that began an hour ago
     * can still be taken, and one that finished yesterday cannot. The board used
     * to key on the start, which dropped jobs that were still perfectly takeable
     * and kept nothing that had actually expired.
     */
    List<SittingRequest> findByStatusAndEndsAtAfterOrderByStartsAtAsc(
            SittingRequestStatus status, Instant notYetOver);

    /** Finished sittings that had a sitter and have not prompted the owner yet. */
    List<SittingRequest> findBySitterIsNotNullAndEndsAtBeforeAndRatingReminderSentAtIsNull(Instant over);

    /** Everything a sitter was accepted for, for their own list. */
    List<SittingRequest> findBySitterOrderByStartsAtAsc(User sitter);
}
