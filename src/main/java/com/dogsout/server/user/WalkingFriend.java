package com.dogsout.server.user;

import java.time.Instant;
import java.util.List;

/**
 * Someone you have matched with who is out walking right now.
 *
 * <p>Only ever built for a match, and only when they chose to share a point —
 * there is deliberately no shape here that can describe a stranger's location.
 */
public record WalkingFriend(
        Long userId,
        String name,
        String profilePicture,
        /** Their dogs' names, so the row can read "Lea is walking Maylie". */
        List<String> dogNames,
        Double latitude,
        Double longitude,
        Instant until,
        /** Rounded, like everywhere else — see DiscoverService.coarseDistanceKm. */
        double distanceKm
) {}
