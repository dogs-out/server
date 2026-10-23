package com.dogsout.server.school;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchoolEventParticipantRepository extends JpaRepository<SchoolEventParticipant, Long> {
    List<SchoolEventParticipant> findByEventOrderByJoinedAtAsc(SchoolEvent event);
    Optional<SchoolEventParticipant> findByEventAndUser(SchoolEvent event, User user);
    long countByEvent(SchoolEvent event);
    List<SchoolEventParticipant> findByUser(User user);
}
