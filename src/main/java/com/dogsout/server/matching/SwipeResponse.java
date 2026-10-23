package com.dogsout.server.matching;

import com.dogsout.server.bones.BoneStatus;

public record SwipeResponse(
        boolean match,
        Long matchId,
        /** The bone counter after this swipe; null when nothing was spent. */
        BoneStatus bones
) {}
