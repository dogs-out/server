package com.dogsout.server.user;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
        LocalDateTime createdAt
) {}