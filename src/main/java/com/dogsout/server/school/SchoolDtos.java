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
            /** For a course, the end of the first class. */
            @NotNull Instant endsAt,
            @Size(max = 200) String placeName,
            @Size(max = 300) String address,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @Min(0) @Max(1_000_000) Integer priceRappen,
            @Min(1) @Max(500) Integer maxParticipants,
            /** A weekly course: its weekdays and last day. startsAt/endsAt are then the first class. */
            List<String> repeatWeekdays,
            java.time.LocalDate repeatUntil,
            /** For a weekly event: "DATE" (pick classes) or "COURSE" (all at once). Defaults to DATE. */
            String signupMode
    ) {}

    /** Which class to sign up for; left out for a one-off or a whole course. */
    public record JoinRequest(java.time.LocalDate date) {}

    /** One upcoming class of a weekly event. */
    public record OccurrenceResponse(java.time.LocalDate date, Instant startsAt, int participantCount,
                                     boolean joined, boolean full) {}

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
            List<PhotoResponse> photos,
            /** A course's weekdays; empty for a one-off. endsAt is then the end of the last class. */
            List<String> repeatWeekdays,
            Integer occurrenceMinutes,
            /** When the next class (or the event) starts; null once it is over. */
            Instant nextStartsAt,
            /** DATE or COURSE for a weekly event; null for a one-off. */
            String signupMode,
            /** The next classes of a weekly event (up to 8), with places per class in DATE mode. */
            List<OccurrenceResponse> occurrences,
            /** COURSE mode: how many classes are still to come — what signing up commits to. */
            int remainingClasses
    ) {}

    /** occurrenceDate: the class signed up for, null for a whole course or a one-off. */
    public record ParticipantResponse(Long userId, String name, String profilePicture, Instant joinedAt,
                                      java.time.LocalDate occurrenceDate) {}

    public record ReviewRequest(@NotBlank String status, @Size(max = 500) String note) {}

    /** What the admin page lists: the school plus who registered it. */
    public record AdminSchoolResponse(SchoolResponse school, Long ownerId, String ownerName, String ownerEmail,
                                      Instant createdAt) {}
}
