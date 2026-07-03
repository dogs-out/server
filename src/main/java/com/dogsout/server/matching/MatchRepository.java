package com.dogsout.server.matching;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    Optional<Match> findByUser1AndUser2(User user1, User user2);

    @Query("SELECT m.user2.id FROM Match m WHERE m.user1.id = :userId")
    List<Long> findSwipedUserIdsByUser1Id(@Param("userId") Long userId);

    @Query("SELECT m FROM Match m WHERE (m.user1.id = :userId OR m.user2.id = :userId) AND m.status = 'MATCHED'")
    List<Match> findAllMatchesForUser(@Param("userId") Long userId);
}
