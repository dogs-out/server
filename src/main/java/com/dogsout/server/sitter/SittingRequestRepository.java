package com.dogsout.server.sitter;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SittingRequestRepository extends JpaRepository<SittingRequest, Long> {

    List<SittingRequest> findByOwnerOrderByStartsAtAsc(User owner);

    /** Open requests that have not already started, soonest first. */
    List<SittingRequest> findByStatusAndStartsAtAfterOrderByStartsAtAsc(
            SittingRequestStatus status, Instant after);
}
