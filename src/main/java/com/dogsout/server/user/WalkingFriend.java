package com.dogsout.server.user;

import java.time.Instant;
import java.util.List;

/**
 * Someone you have matched with who is out walking right now.
 *
 * <p>Only ever built for a match — there is deliberately no shape here that can
 * describe a stranger's location. The point itself is optional: null latitude and
 * longitude mean they said they are out without saying where.
 */
public record WalkingFriend(
        Long userId,
        String name,
        String profilePicture,
        /** Their dogs' names, so the row can read "Lea is walking Maylie". */
        List<String> dogNames,
        /** Null where they are walking but chose not to share where. */
        Double latitude,
        Double longitude,
        Instant until,
        /** Rounded, like everywhere else — see DiscoverService.coarseDistanceKm; -1 when unknown. */
        double distanceKm
) {}
