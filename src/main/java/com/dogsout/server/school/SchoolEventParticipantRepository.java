package com.dogsout.server.school;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SchoolEventParticipantRepository extends JpaRepository<SchoolEventParticipant, Long> {
    List<SchoolEventParticipant> findByEventOrderByJoinedAtAsc(SchoolEvent event);
    List<SchoolEventParticipant> findByEventAndUser(SchoolEvent event, User user);
    Optional<SchoolEventParticipant> findFirstByEventAndUserAndOccurrenceDateIsNull(SchoolEvent event, User user);
    Optional<SchoolEventParticipant> findFirstByEventAndUserAndOccurrenceDate(SchoolEvent event, User user, LocalDate date);
    List<SchoolEventParticipant> findByEventAndOccurrenceDate(SchoolEvent event, LocalDate date);
    long countByEvent(SchoolEvent event);
    long countByEventAndOccurrenceDate(SchoolEvent event, LocalDate date);
    List<SchoolEventParticipant> findByUser(User user);
}
