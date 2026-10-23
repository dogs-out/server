package com.dogsout.server.school;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DogSchoolRepository extends JpaRepository<DogSchool, Long> {
    Optional<DogSchool> findByOwner(User owner);
    List<DogSchool> findByStatusOrderByCreatedAtAsc(String status);
}
