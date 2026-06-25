package com.dogsout.server.dog;

public record DogRequest(
        String name,
        String breed,
        Integer age,
        String bio,
        String profilePicture
) {}