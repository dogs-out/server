package com.dogsout.server.sitter;

import jakarta.validation.constraints.Size;

/**
 * What the owner hands over once somebody is coming.
 *
 * @param todoList       one instruction per line; feeding, keys, the usual walk
 * @param emergencyPhone who to ring when the owner cannot be reached
 * @param addressLabel   where the sitting happens, as the owner wrote or picked it
 */
public record SittingDetailsRequest(
        @Size(max = 2000) String todoList,
        @Size(max = 40) String emergencyPhone,
        @Size(max = 200) String addressLabel,
        Double addressLatitude,
        Double addressLongitude
) {}
