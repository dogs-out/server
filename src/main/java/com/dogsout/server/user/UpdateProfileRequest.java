package com.dogsout.server.user;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record UpdateProfileRequest(
        @Size(max = 100)
        @Pattern(regexp = "^[\\p{L}\\s'\\-]+$", message = "Name may only contain letters, spaces, hyphens, and apostrophes")
        String name,
        @Size(max = 250) String bio,
        LocalDate dateOfBirth,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Size(max = 2_000_000) String profilePicture,
        List<String> lifestyleTags,
        List<String> personalityTags,
        String relationshipStatus
) {}