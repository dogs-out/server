package com.dogsout.server.sitter;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SitterCancellationRepository extends JpaRepository<SitterCancellation, Long> {

    /** Late cancellations inside the rolling window, newest first. */
    List<SitterCancellation> findBySitterAndLateIsTrueAndCreatedAtAfterOrderByCreatedAtDesc(
            User sitter, Instant since);

    long countBySitterAndLateIsTrueAndCreatedAtAfter(User sitter, Instant since);
}
