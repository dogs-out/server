package com.dogsout.server.sitter;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SitterReviewRepository extends JpaRepository<SitterReview, Long> {

    Optional<SitterReview> findByRequest(SittingRequest request);

    boolean existsByRequest(SittingRequest request);

    /** A sitter's reviews, newest first; hidden ones are left out of public views. */
    List<SitterReview> findBySitterAndHiddenAtIsNullOrderByCreatedAtDesc(User sitter);

    @Query("select coalesce(avg(r.stars), 0) from SitterReview r where r.sitter = :sitter and r.hiddenAt is null")
    double averageStars(User sitter);

    long countBySitterAndHiddenAtIsNull(User sitter);
}
