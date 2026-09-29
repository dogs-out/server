package com.dogsout.server.sos;

import com.dogsout.server.dog.DogResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class SosDtos {

    private SosDtos() {}

    public record RaiseAlertRequest(
            @NotNull Long dogId,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @Size(max = 200) String placeName,
            @Size(max = 1000) String note
    ) {}

    public record CloseAlertRequest(boolean found) {}

    public record ContactOwnerResponse(Long matchId) {}

    public record AlertResponse(
            Long id,
            DogResponse dog,
            Long ownerId,
            String ownerName,
            String ownerProfilePicture,
            Double latitude,
            Double longitude,
            String placeName,
            String note,
            Instant createdAt,
            boolean open,
            boolean found,
            boolean mine,
            /** From the viewer's saved location; null when they have none. */
            Double distanceKm
    ) {}
}
