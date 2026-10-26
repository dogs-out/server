package com.dogsout.server.market;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class MarketDtos {

    private MarketDtos() {}

    /** Dog things only; "OTHER" is still for dogs. Strings, never an enum column. */
    public static final List<String> CATEGORIES = List.of(
            "LEASH_COLLAR", "BED", "TOYS", "FOOD", "CRATE_TRANSPORT", "CLOTHING", "CARE", "OTHER");

    public record ListingRequest(
            @NotBlank @Size(max = 80) String title,
            @Size(max = 1000) String description,
            /** Rappen; null for free. At most CHF 10,000 — this is not for selling cars. */
            @Min(0) @Max(1_000_000) Integer priceRappen,
            @NotBlank String category
    ) {}

    public record StatusRequest(@NotBlank String status) {}

    public record PhotoResponse(Long id, String url, String thumbUrl) {}

    public record ListingResponse(
            Long id,
            String title,
            String description,
            Integer priceRappen,
            String category,
            String status,
            List<PhotoResponse> photos,
            Long sellerId,
            String sellerName,
            String sellerPicture,
            /** Whole km, -1 when either side has no location. */
            double distanceKm,
            Instant createdAt,
            boolean mine,
            /** The viewer's conversation about it, if they already asked. */
            Long myConversationId,
            /** For the seller: how many people asked. */
            int conversationCount
    ) {}

    public record ConversationSummary(
            Long id,
            Long listingId,
            String listingTitle,
            String listingPhoto,
            String listingStatus,
            Long otherUserId,
            String otherName,
            String otherPicture,
            boolean iAmSeller,
            String lastMessage,
            Instant lastMessageAt,
            boolean unread
    ) {}

    public record MessageRequest(@NotBlank @Size(max = 2000, message = "A message can be up to 2000 characters.") String content) {}

    public record MessageResponse(Long id, Long conversationId, Long senderId, String content, Instant sentAt) {}
}
