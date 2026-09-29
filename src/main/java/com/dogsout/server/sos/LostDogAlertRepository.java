package com.dogsout.server.sos;

import com.dogsout.server.dog.Dog;
import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface LostDogAlertRepository extends JpaRepository<LostDogAlert, Long> {

    List<LostDogAlert> findByClosedAtIsNullAndCreatedAtAfter(Instant since);

    List<LostDogAlert> findByOwnerAndClosedAtIsNullAndCreatedAtAfter(User owner, Instant since);

    long countByOwnerAndCreatedAtAfter(User owner, Instant since);

    @Transactional
    void deleteByOwner(User owner);

    @Transactional
    void deleteByDog(Dog dog);
}
