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
        Boolean hasDog,
        Boolean isSitter,
        Boolean lookingForSitter,
        List<String> sitterWeekdays,
        Integer sitterExperienceYears,
        List<String> sitterTags,
        LocalDateTime createdAt,
        List<UserPhotoResponse> photos,
        Integer maxDistanceKm,
        Integer minAge,
        Integer maxAge,
        Integer minDogAge,
        Integer maxDogAge,
        Boolean notificationsEnabled,
        boolean termsAccepted,
        /** Null once it has expired — see UserService.activeStatus. */
        String walkStatus,
        java.time.Instant walkStatusExpiresAt,
        /** The three below let the status screen open on what is already set. */
        Double walkStatusLatitude,
        Double walkStatusLongitude,
        String walkStatusPlaceName,
        Long walkStatusDogId,
        /** Today is this account's birthday, or one of its dogs'. Month and day only. */
        boolean celebratingToday
) {}