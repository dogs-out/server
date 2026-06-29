package com.dogsout.server.user;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(
        Long id,
        String email,
        String name,
        LocalDate dateOfBirth,
        String bio,
        String profilePicture,
        Double latitude,
        Double longitude,
        String role,
        String authProvider,
        List<String> lifestyleTags,
        List<String> personalityTags,
        String relationshipStatus,
        LocalDateTime createdAt,
        List<UserPhotoResponse> photos
) {}