package com.dogsout.server.auth;

public record AuthResponse(
        String token,
        String email,
        String name
) {}