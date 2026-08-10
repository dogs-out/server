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
        // No profilePicture: it is derived from the dog's sortOrder == 0 photo and set
        // by the photo endpoints, so there is nothing for a client to send here.
        Integer energyLevel,
        String socialBehavior,
        List<String> loves,
        String offLeash,
        Integer kidsComfort,
        List<String> tags
) {}