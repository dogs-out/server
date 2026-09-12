package com.dogsout.server.user;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * @param status    null clears the status entirely.
 * @param hours     how long it stands; clamped to the range the status allows.
 * @param latitude  optional, and ignored for statuses that may not carry a point.
 * @param placeName what that point is called, when it was picked on the map.
 * @param dogId     the dog being looked after; required by SITTING, ignored otherwise.
 * @param keepPhoto keeps a photo already attached; absent means the status change drops it.
 */
public record UpdateStatusRequest(
        WalkStatus status,
        @Min(1) @Max(504) Integer hours,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Size(max = 120) String placeName,
        Long dogId,
        Boolean keepPhoto
) {}
