package com.dogsout.server.school;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SchoolEventRepository extends JpaRepository<SchoolEvent, Long> {
    List<SchoolEvent> findBySchoolOrderByStartsAtAsc(DogSchool school);
    List<SchoolEvent> findByCancelledFalseAndEndsAtAfter(Instant after);
}
