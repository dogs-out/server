package com.dogsout.server.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByResetToken(String resetToken);
    Optional<User> findByAppleUserId(String appleUserId);

    /** Users whose birthday is today and who have not been greeted this year. */
    @Query(value = """
            SELECT * FROM users
             WHERE date_of_birth IS NOT NULL
               AND EXTRACT(MONTH FROM date_of_birth) = :month
               AND EXTRACT(DAY FROM date_of_birth) = :day
               AND (birthday_greeted_year IS NULL OR birthday_greeted_year <> :year)
            """, nativeQuery = true)
    List<User> findBirthdaysOn(@Param("month") int month, @Param("day") int day, @Param("year") int year);

    /** Touches only the activity columns, so it can never overwrite a concurrent profile save. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("""
            update User u set u.lastActiveAt = :at,
                u.lastPlatform = coalesce(:platform, u.lastPlatform),
                u.lastAppVersion = coalesce(:version, u.lastAppVersion)
            where u.email = :email""")
    int recordActivity(String email, java.time.Instant at, String platform, String version);

    @org.springframework.data.jpa.repository.Query("select u.id from User u where u.email = :email")
    Long findIdByEmail(String email);
}
