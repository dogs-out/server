package com.dogsout.server.matching;

import com.dogsout.server.dog.DogResponse;
import com.dogsout.server.user.UserPhotoResponse;

import java.util.List;

public record DiscoverProfile(
        Long userId,
        String name,
        Integer age,
        String bio,
        String profilePicture,
        List<UserPhotoResponse> photos,
        List<String> lifestyleTags,
        List<String> personalityTags,
        String relationshipStatus,
        List<DogResponse> dogs,
        double distanceKm,
        boolean isSitter,
        List<String> sitterWeekdays,
        Integer sitterExperienceYears,
        List<String> sitterTags,
        boolean lookingForSitter,
        /**
         * True when this person, or one of their dogs, has a birthday today.
         *
         * <p>A boolean rather than the date: the exact birth date is deliberately
         * never exposed anywhere in this DTO, and the app only needs to know
         * whether to dress the chat up.
         */
        boolean celebratingToday,
        /** Their current status, or null if they have none or it has run out. */
        String walkStatus
) {}
