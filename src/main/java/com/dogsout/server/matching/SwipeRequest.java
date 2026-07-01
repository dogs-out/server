package com.dogsout.server.matching;

public record SwipeRequest(
        Long targetUserId,
        String action
) {}
