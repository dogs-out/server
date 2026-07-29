package com.dogsout.server.playdate;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaydateParticipantRepository extends JpaRepository<PlaydateParticipant, Long> {

    List<PlaydateParticipant> findByPlaydate(Playdate playdate);

    Optional<PlaydateParticipant> findByPlaydateAndUser(Playdate playdate, User user);

    List<PlaydateParticipant> findByUser(User user);

    long countByPlaydateAndStatus(Playdate playdate, ParticipantStatus status);

    void deleteByPlaydate(Playdate playdate);

    void deleteByUser(User user);
}
