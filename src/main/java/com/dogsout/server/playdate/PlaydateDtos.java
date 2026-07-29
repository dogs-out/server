package com.dogsout.server.playdate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class PlaydateDtos {

    private PlaydateDtos() {}

    public record CreatePlaydateRequest(
            @Size(max = 100) String title,
            @Size(max = 1000) String description,
            @NotBlank @Size(max = 200) String parkName,
            @Size(max = 300) String address,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @NotNull Instant startsAt,
            Integer maxParticipants,
            @NotNull PlaydateVisibility visibility,
            List<Long> inviteUserIds
    ) {}

    public record UpdatePlaydateRequest(
            @Size(max = 100) String title,
            @Size(max = 1000) String description,
            @NotBlank @Size(max = 200) String parkName,
            @Size(max = 300) String address,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @NotNull Instant startsAt,
            Integer maxParticipants
    ) {}

    public record ParticipantResponse(
            Long userId,
            String name,
            String profilePicture,
            String status
    ) {}

    public record PlaydateResponse(
            Long id,
            Long hostId,
            String hostName,
            String hostProfilePicture,
            String title,
            String description,
            String parkName,
            String address,
            Double latitude,
            Double longitude,
            Instant startsAt,
            Integer maxParticipants,
            String visibility,
            String status,
            int joinedCount,
            String myStatus, // HOST | JOINED | INVITED | NONE
            List<ParticipantResponse> participants
    ) {}

    public record PlaydateMessageResponse(
            Long id,
            Long senderId,
            String senderName,
            String senderProfilePicture,
            String content,
            Instant sentAt
    ) {}

    public record InviteRequest(@NotNull List<Long> userIds) {}

    public record SendPlaydateMessageRequest(@NotBlank @Size(max = 2000) String content) {}
}
