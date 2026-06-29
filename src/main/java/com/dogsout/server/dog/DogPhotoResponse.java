package com.dogsout.server.dog;

public record DogPhotoResponse(Long id, String imageData, Integer sortOrder) {}