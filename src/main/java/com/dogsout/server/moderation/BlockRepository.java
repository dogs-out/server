package com.dogsout.server.moderation;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlockRepository extends JpaRepository<Block, Long> {

    Optional<Block> findByBlockerAndBlocked(User blocker, User blocked);

    List<Block> findByBlockerOrderByCreatedAtDesc(User blocker);

    @Query("""
            SELECT COUNT(b) > 0 FROM Block b
            WHERE (b.blocker.id = :userA AND b.blocked.id = :userB)
               OR (b.blocker.id = :userB AND b.blocked.id = :userA)
            """)
    boolean existsBlockBetween(@Param("userA") Long userA, @Param("userB") Long userB);

    @Query("SELECT b.blocked.id FROM Block b WHERE b.blocker.id = :userId")
    List<Long> findBlockedIdsByBlockerId(@Param("userId") Long userId);

    @Query("SELECT b.blocker.id FROM Block b WHERE b.blocked.id = :userId")
    List<Long> findBlockerIdsByBlockedId(@Param("userId") Long userId);

    void deleteByBlockerOrBlocked(User blocker, User blocked);
}