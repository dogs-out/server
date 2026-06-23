package com.dogsout.server.user;

import java.time.LocalDate;

public record UpdateProfileRequest(
        String name,
        String bio,
        LocalDate dateOfBirth,
        Double latitude,
        Double longitude,
        String profilePicture
) {}