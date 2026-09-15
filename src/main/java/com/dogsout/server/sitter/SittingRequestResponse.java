package com.dogsout.server.sitter;

import java.time.Instant;
import java.util.List;

/**
 * An open job as a sitter sees it.
 *
 * @param dogs     which dogs, by name, because one calm terrier and three is not the same job
 * @param mine     whether the reader posted it, so the list can offer cancelling instead of contacting
 * @param distanceKm rounded, like everywhere else; -1 when either side has no location
 */
public record SittingRequestResponse(
        Long id,
        Long ownerId,
        String ownerName,
        String ownerProfilePicture,
        Instant startsAt,
        Instant endsAt,
        List<String> dogs,
        String note,
        String status,
        boolean mine,
        double distanceKm
) {}
