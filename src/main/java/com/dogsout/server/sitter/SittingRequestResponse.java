package com.dogsout.server.sitter;

import java.time.Instant;
import java.util.List;

/**
 * A sitting job as whoever is reading it sees it.
 *
 * @param dogs        which dogs, by name, because one calm terrier and three is not the same job
 * @param mine        whether the reader posted it, so the list can offer managing instead of offering
 * @param sitterId    who got the job, or null while it is still open
 * @param over        the window has passed; the job board drops these and the owner's list greys them
 * @param canEdit     the reader may still change it — their own, not accepted, not over
 * @param awaitingReview  it is over, somebody sat, and the owner has not rated them yet
 * @param distanceKm  rounded, like everywhere else; -1 when either side has no location
 * @param detailsShared the owner has handed over the to-do list, number and address
 */
public record SittingRequestResponse(
        Long id,
        Long ownerId,
        String ownerName,
        String ownerProfilePicture,
        Instant startsAt,
        Instant endsAt,
        List<String> dogs,
        List<Long> dogIds,
        String note,
        String status,
        boolean mine,
        Long sitterId,
        String sitterName,
        String sitterProfilePicture,
        boolean over,
        boolean canEdit,
        boolean awaitingReview,
        double distanceKm,
        String todoList,
        String emergencyPhone,
        String addressLabel,
        Double addressLatitude,
        Double addressLongitude,
        boolean detailsShared
) {}
