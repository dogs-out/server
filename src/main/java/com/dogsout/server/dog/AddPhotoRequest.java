package com.dogsout.server.dog;

import jakarta.validation.constraints.NotBlank;

public record AddPhotoRequest(@NotBlank String imageData) {}