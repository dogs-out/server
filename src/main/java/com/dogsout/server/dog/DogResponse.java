package com.dogsout.server.dog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DogResponse(
        Long id,
        String name,
        String breed,
        LocalDate dateOfBirth,
        String bio,
        String profilePicture,
        Long ownerId,
        String ownerName,
        String ownerProfilePicture,
        LocalDateTime createdAt,
        Integer energyLevel,
        String socialBehavior,
        List<String> loves,
        String offLeash,
        Integer kidsComfort,
        List<String> tags,
        List<DogPhotoResponse> photos
) {}