package com.dogsout.server.user;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * @param status      null clears the status entirely.
 * @param hours       how long it stands, from an hour to four weeks.
 * @param latitude    optional, and ignored for statuses that may not carry a point.
 */
public record UpdateStatusRequest(
        WalkStatus status,
        @Min(1) @Max(672) Integer hours,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
) {}
