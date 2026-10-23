package com.dogsout.server.school;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class SchoolDtos {

    private SchoolDtos() {}

    public record SchoolRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 1500) String description,
            @NotBlank @Size(max = 300) String address,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @Size(max = 40) String phone,
            @Size(max = 200) String website,
            @Size(max = 1000) String openingHours
    ) {}

    public record SchoolResponse(
            Long id,
            String name,
            String description,
            String address,
            Double latitude,
            Double longitude,
            String phone,
            String website,
            String openingHours,
            String logo,
            /** PENDING, APPROVED or REJECTED; only the owner ever sees anything but APPROVED. */
            String status,
            String reviewNote,
            double distanceKm,
            boolean mine,
            List<EventResponse> upcomingEvents
    ) {}

    public record EventRequest(
            @NotBlank @Size(max = 100) String title,
            @Size(max = 2000) String description,
            @NotNull Instant startsAt,
            Instant endsAt,
            @Size(max = 200) String placeName,
            @Size(max = 300) String address,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @Min(0) @Max(1_000_000) Integer priceRappen,
            @Min(1) @Max(500) Integer maxParticipants
    ) {}

    public record PhotoResponse(Long id, String url, String thumbUrl) {}

    public record EventResponse(
            Long id,
            Long schoolId,
            String schoolName,
            String schoolLogo,
            String title,
            String description,
            Instant startsAt,
            Instant endsAt,
            String placeName,
            String address,
            Double latitude,
            Double longitude,
            Integer priceRappen,
            Integer maxParticipants,
            int participantCount,
            boolean joined,
            boolean mine,
            boolean cancelled,
            double distanceKm,
            List<PhotoResponse> photos
    ) {}

    public record ParticipantResponse(Long userId, String name, String profilePicture, Instant joinedAt) {}

    public record ReviewRequest(@NotBlank String status, @Size(max = 500) String note) {}

    /** What the admin page lists: the school plus who registered it. */
    public record AdminSchoolResponse(SchoolResponse school, Long ownerId, String ownerName, String ownerEmail,
                                      Instant createdAt) {}
}
