package com.dogsout.server.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserActivityDayRepository extends JpaRepository<UserActivityDay, Long> {

    boolean existsByUserIdAndDay(Long userId, java.time.LocalDate day);

    /** Account deletion. */
    void deleteByUserId(Long userId);
}
