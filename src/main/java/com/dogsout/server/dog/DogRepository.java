package com.dogsout.server.dog;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DogRepository extends JpaRepository<Dog, Long> {
    List<Dog> findByOwner(User owner);
    long countByOwner(User owner);

    /**
     * Dogs whose birthday falls today and who have not been greeted this year.
     *
     * <p>Native because the comparison is on the month and day of a date column,
     * which JPQL cannot express portably. The greeted-year check is part of the
     * query rather than a filter afterwards so a restart mid-run cannot re-greet
     * the dogs it already handled.
     */
    @Query(value = """
            SELECT * FROM dogs
             WHERE EXTRACT(MONTH FROM date_of_birth) = :month
               AND EXTRACT(DAY FROM date_of_birth) = :day
               AND (birthday_greeted_year IS NULL OR birthday_greeted_year <> :year)
            """, nativeQuery = true)
    List<Dog> findBirthdaysOn(@Param("month") int month, @Param("day") int day, @Param("year") int year);
}