package com.dogsout.server.dog;

import java.time.LocalDateTime;

public record DogResponse(
        Long id,
        String name,
        String breed,
        Integer age,
        String bio,
        String profilePicture,
        Long ownerId,
        String ownerName,
        LocalDateTime createdAt
) {}