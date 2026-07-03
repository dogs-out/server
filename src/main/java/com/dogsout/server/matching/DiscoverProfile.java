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
        double distanceKm
) {}
