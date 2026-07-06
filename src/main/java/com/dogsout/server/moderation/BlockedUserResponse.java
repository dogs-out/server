package com.dogsout.server.moderation;

import java.time.Instant;

public record BlockedUserResponse(
        Long userId,
        String name,
        String profilePicture,
        Instant blockedAt
) {}