package com.dogsout.server.notification;

import com.dogsout.server.bones.BoneStatus;

/** Today's news for one user: what the daily push and the in-app card are made of. */
public record DailyDigest(
        /** People who joined this week and would show up in their deck. */
        int newNearby,
        /** Everyone currently waiting in their deck. */
        int deckSize,
        BoneStatus bones,
        /** Whether they can host a playdate (owners only). */
        boolean canHost
) {}
