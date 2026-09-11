package com.dogsout.server.user;

import java.time.Instant;
import java.util.List;

/**
 * Someone you have matched with who is out right now.
 *
 * <p>Only ever built for a match — there is deliberately no shape here that can
 * describe a stranger's location. The point itself is optional: null latitude and
 * longitude mean they said they are out without saying where.
 */
public record WalkingFriend(
        Long userId,
        String name,
        String profilePicture,
        /** Their own dogs, or the one they are looking after when sitting. */
        List<String> dogNames,
        /** WALKING, AT_THE_PARK or SITTING — the row is worded from this. */
        String status,
        /** Null where they are out but chose not to share where. */
        Double latitude,
        Double longitude,
        /** What the point is called, when it was picked on the map rather than measured. */
        String placeName,
        Instant until,
        /** Rounded, like everywhere else — see DiscoverService.coarseDistanceKm; -1 when unknown. */
        double distanceKm
) {}
