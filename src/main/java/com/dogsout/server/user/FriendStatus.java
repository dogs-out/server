package com.dogsout.server.user;

import java.time.Instant;
import java.util.List;

/**
 * Someone you have matched with, and what they are up to.
 *
 * <p>Every match appears, not only the ones who are out: knowing a friend is away
 * for a fortnight is worth as much as knowing they are at the park, and a list
 * that empties itself whenever nobody is walking tells you nothing at all.
 *
 * <p>Only ever built for a match — there is deliberately no shape here that can
 * describe a stranger's location. The point is optional and absent for everyone
 * whose status is not about a place.
 */
public record FriendStatus(
        Long userId,
        String name,
        String profilePicture,
        /** Their own dogs, or the one they are looking after when sitting. */
        List<String> dogNames,
        /** Never null: somebody who never picked one is at home. The row is worded from this. */
        String status,
        /** Null unless they are somewhere worth joining and chose to share it. */
        Double latitude,
        Double longitude,
        /** What the point is called, when it was picked on the map rather than measured. */
        String placeName,
        /** Optional photo they attached to the status. */
        String photo,
        Instant until,
        /** Rounded, like everywhere else — see DiscoverService.coarseDistanceKm; -1 when unknown. */
        double distanceKm
) {}
