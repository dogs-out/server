package com.dogsout.server.dog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record DogRequest(
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[\\p{L}\\s'\\-]+$", message = "Name may only contain letters, spaces, hyphens, and apostrophes")
        String name,
        @Size(max = 100) String breed,
        LocalDate dateOfBirth,
        @Size(max = 250) String bio,
        @Size(max = 2_000_000) String profilePicture,
        Integer energyLevel,
        String socialBehavior,
        List<String> loves,
        String offLeash,
        Integer kidsComfort,
        List<String> tags
) {}