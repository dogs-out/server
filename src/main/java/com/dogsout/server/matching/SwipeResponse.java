package com.dogsout.server.matching;

public record SwipeResponse(
        boolean match,
        Long matchId
) {}
