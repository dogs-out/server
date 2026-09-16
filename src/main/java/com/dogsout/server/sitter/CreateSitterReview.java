package com.dogsout.server.sitter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param stars    required; the rest of a review is optional on purpose
 * @param tags     highlight tags, at most three, validated against the allowed list
 */
public record CreateSitterReview(
        @NotNull Long requestId,
        @Min(SitterReview.MIN_STARS) @Max(SitterReview.MAX_STARS) int stars,
        @Size(max = 1000) String comment,
        List<String> tags
) {}
