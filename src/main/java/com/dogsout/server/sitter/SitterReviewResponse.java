package com.dogsout.server.sitter;

import java.time.Instant;
import java.util.List;

/**
 * One review, as it appears on a sitter's profile.
 *
 * @param comment null when none was written, or when it has been hidden pending moderation
 */
public record SitterReviewResponse(
        Long id,
        Long sitterId,
        Long raterId,
        String raterName,
        String raterProfilePicture,
        int stars,
        String comment,
        List<String> tags,
        boolean mine,
        Instant createdAt
) {}
