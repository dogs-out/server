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
        /**
         * Never null: an account that has not picked one reads as AT_HOME, which
         * is where everyone starts. Expired statuses fall back to it too.
         */
        String walkStatus,
        java.time.Instant walkStatusExpiresAt,
        /** The three below let the status screen open on what is already set. */
        Double walkStatusLatitude,
        Double walkStatusLongitude,
        String walkStatusPlaceName,
        Long walkStatusDogId,
        /** Optional photo attached to the current status. */
        String walkStatusPhoto,
        /** Today is this account's birthday, or one of its dogs'. Month and day only. */
        boolean celebratingToday,
        /** Today is the person's own birthday. */
        boolean birthdayToday,
        /** Names of their dogs whose birthday is today, so a greeting can say which. */
        java.util.List<String> dogBirthdaysToday
) {}