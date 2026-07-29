package com.dogsout.server.playdate;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PlaydateRepository extends JpaRepository<Playdate, Long> {

    List<Playdate> findByHost(User host);

    List<Playdate> findByStatusAndStartsAtAfter(PlaydateStatus status, Instant after);
}
