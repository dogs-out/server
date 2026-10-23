package com.dogsout.server.market;

import com.dogsout.server.GeoUtil;
import com.dogsout.server.ProfanityFilter;
import com.dogsout.server.market.MarketDtos.ConversationSummary;
import com.dogsout.server.market.MarketDtos.ListingRequest;
import com.dogsout.server.market.MarketDtos.ListingResponse;
import com.dogsout.server.market.MarketDtos.MessageResponse;
import com.dogsout.server.market.MarketDtos.PhotoResponse;
import com.dogsout.server.moderation.BlockRepository;
import com.dogsout.server.moderation.ModerationService;
import com.dogsout.server.moderation.ReportRequest;
import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.photo.PhotoRendition;
import com.dogsout.server.photo.PhotoService;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import com.dogsout.server.websocket.ChatSocketEvent;
import com.dogsout.server.websocket.ChatSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The marketplace: dog things passed on between owners nearby.
 *
 * <p>Dogs Out handles no payment and no delivery — a listing is a notice, and the
 * people arrange pickup and payment in the listing's own chat. That chat is not a
 * match, so asking about a lead never puts a stranger among your matches.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MarketService {

    public static final String OWNER_LISTING = "listing";
    static final int MAX_PHOTOS = 5;
    private static final double DEFAULT_RADIUS_KM = 50;

    private final ListingRepository listings;
    private final ListingPhotoRepository photos;
    private final ListingConversationRepository conversations;
    private final ListingMessageRepository messages;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;
    private final ModerationService moderationService;
    private final ProfanityFilter profanityFilter;
    private final PhotoService photoService;
    private final ChatSocketHandler chatSocketHandler;
    private final PushNotificationService pushNotificationService;

    // ─── Browsing ─────────────────────────────────────────────────────────────

    /** Listings near me, newest first; sold ones and my own are left out. */
    @Transactional(readOnly = true)
    public List<ListingResponse> browse(String email, String category) {
        User me = findUser(email);
        Set<Long> blocked = blockedIds(me);
        boolean located = me.getLatitude() != null && me.getLongitude() != null;
        double radius = me.getMaxDistanceKm() != null ? me.getMaxDistanceKm() : DEFAULT_RADIUS_KM;

        return listings.findByStatusInOrderByCreatedAtDesc(List.of(Listing.ACTIVE, Listing.RESERVED)).stream()
                .filter(l -> !Boolean.TRUE.equals(l.getHiddenByReport()))
                .filter(l -> !l.getSeller().getId().equals(me.getId()))
                .filter(l -> !blocked.contains(l.getSeller().getId()))
                .filter(l -> category == null || category.isBlank() || category.equals(l.getCategory()))
                // Pickup only, so distance is the whole point: without a location of
                // my own there is nothing sensible to show.
                .filter(l -> located && l.getLatitude() != null
                        && GeoUtil.distanceKm(me.getLatitude(), me.getLongitude(), l.getLatitude(), l.getLongitude()) <= radius)
                .map(l -> toResponse(l, me))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> mine(String email) {
        User me = findUser(email);
        return listings.findBySellerOrderByCreatedAtDesc(me).stream().map(l -> toResponse(l, me)).toList();
    }

    @Transactional(readOnly = true)
    public ListingResponse get(String email, Long id) {
        User me = findUser(email);
        return toResponse(viewable(id, me), me);
    }

    // ─── Selling ──────────────────────────────────────────────────────────────

    public ListingResponse create(String email, ListingRequest request) {
        User me = findUser(email);
        if (me.getLatitude() == null || me.getLongitude() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Set your location first, so buyers know where to pick it up");
        }
        validate(request);
        Listing listing = new Listing();
        listing.setSeller(me);
        apply(listing, request);
        // Rounded to about a kilometre: close enough to judge the trip, not a front door.
        listing.setLatitude(Math.round(me.getLatitude() * 100) / 100.0);
        listing.setLongitude(Math.round(me.getLongitude() * 100) / 100.0);
        return toResponse(listings.save(listing), me);
    }

    public ListingResponse update(String email, Long id, ListingRequest request) {
        User me = findUser(email);
        Listing listing = owned(id, me);
        validate(request);
        apply(listing, request);
        return toResponse(listings.save(listing), me);
    }

    public ListingResponse setStatus(String email, Long id, String status) {
        User me = findUser(email);
        Listing listing = owned(id, me);
        if (!List.of(Listing.ACTIVE, Listing.RESERVED, Listing.SOLD).contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status");
        }
        listing.setStatus(status);
        return toResponse(listings.save(listing), me);
    }

    public void delete(String email, Long id) {
        User me = findUser(email);
        deleteListing(owned(id, me)).forEach(photoService::delete);
    }

    public ListingResponse addPhoto(String email, Long id, MultipartFile file) {
        User me = findUser(email);
        Listing listing = owned(id, me);
        List<ListingPhoto> existing = photos.findByListingOrderBySortOrderAsc(listing);
        if (existing.size() >= MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most " + MAX_PHOTOS + " photos");
        }
        ListingPhoto photo = new ListingPhoto();
        photo.setListing(listing);
        photo.setStorageKey(photoService.store(OWNER_LISTING, file));
        photo.setSortOrder(existing.size());
        photos.save(photo);
        return toResponse(listing, me);
    }

    public ListingResponse deletePhoto(String email, Long id, Long photoId) {
        User me = findUser(email);
        Listing listing = owned(id, me);
        ListingPhoto photo = photos.findById(photoId)
                .filter(p -> p.getListing().getId().equals(listing.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
        photos.delete(photo);
        photoService.delete(photo.getStorageKey());
        return toResponse(listing, me);
    }

    /** Hidden at once — the rules (no animals, no medicines) are about harm — then looked at. */
    public void report(String email, Long id, ReportRequest request) {
        User me = findUser(email);
        Listing listing = viewable(id, me);
        if (listing.getSeller().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot report your own listing");
        }
        listing.setHiddenByReport(true);
        listings.save(listing);
        moderationService.reportListing(me, listing.getSeller(), listing.getId(), listing.getTitle(),
                listing.getDescription(), request);
    }

    // ─── Conversations ────────────────────────────────────────────────────────

    /** The buyer's conversation about a listing, started if it does not exist yet. */
    public ConversationSummary openConversation(String email, Long listingId) {
        User me = findUser(email);
        Listing listing = viewable(listingId, me);
        if (listing.getSeller().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This is your own listing");
        }
        if (Listing.SOLD.equals(listing.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This has already been sold");
        }
        ListingConversation conversation = conversations.findByListingAndBuyer(listing, me).orElseGet(() -> {
            ListingConversation c = new ListingConversation();
            c.setListing(listing);
            c.setBuyer(me);
            return conversations.save(c);
        });
        return summary(conversation, me);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> conversations(String email) {
        User me = findUser(email);
        Set<Long> blocked = blockedIds(me);
        return conversations.findInvolving(me).stream()
                .filter(c -> !blocked.contains(other(c, me).getId()))
                // Opened but never written in: nothing to list yet.
                .filter(c -> c.getLastMessageAt() != null)
                .sorted(Comparator.comparing(ListingConversation::getLastMessageAt).reversed())
                .map(c -> summary(c, me))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationSummary conversation(String email, Long id) {
        User me = findUser(email);
        return summary(participantOf(id, me), me);
    }

    public List<MessageResponse> messages(String email, Long conversationId) {
        User me = findUser(email);
        ListingConversation c = participantOf(conversationId, me);
        markRead(c, me);
        return messages.findByConversationOrderBySentAtAsc(c).stream().map(MarketService::toMessage).toList();
    }

    public MessageResponse send(String email, Long conversationId, String content) {
        User me = findUser(email);
        ListingConversation c = participantOf(conversationId, me);
        User other = other(c, me);
        if (blockRepository.existsBlockBetween(me.getId(), other.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This user is not available");
        }
        if (profanityFilter.containsProfanity(content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your message contains inappropriate language.");
        }
        ListingMessage message = new ListingMessage();
        message.setConversation(c);
        message.setSender(me);
        message.setContent(content.trim());
        message = messages.save(message);
        Instant now = message.getSentAt() != null ? message.getSentAt() : Instant.now();
        c.setLastMessageAt(now);
        markRead(c, me);

        MessageResponse response = toMessage(message);
        chatSocketHandler.sendToUser(other.getId(), ChatSocketEvent.listingMessage(c.getId(), response));
        if (!chatSocketHandler.isOnline(other.getId())) {
            pushNotificationService.send(other, me.getName() + " · " + c.getListing().getTitle(),
                    content.length() > 120 ? content.substring(0, 117) + "…" : content,
                    Map.of("type", "LISTING_MESSAGE", "conversationId", c.getId()));
        }
        return response;
    }

    // ─── Account deletion ─────────────────────────────────────────────────────

    /** Everything of theirs in the marketplace; returns photo keys to delete after the rows. */
    public List<String> deleteAllForUser(User user) {
        List<String> keys = new ArrayList<>();
        for (ListingConversation c : conversations.findByBuyer(user)) {
            messages.deleteByConversation(c);
            conversations.delete(c);
        }
        for (Listing l : listings.findBySellerOrderByCreatedAtDesc(user)) keys.addAll(deleteListing(l));
        return keys;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private List<String> deleteListing(Listing listing) {
        for (ListingConversation c : conversations.findByListing(listing)) {
            messages.deleteByConversation(c);
            conversations.delete(c);
        }
        List<ListingPhoto> ps = photos.findByListingOrderBySortOrderAsc(listing);
        List<String> keys = ps.stream().map(ListingPhoto::getStorageKey).toList();
        photos.deleteAll(ps);
        listings.delete(listing);
        return keys;
    }

    private void validate(ListingRequest r) {
        if (!MarketDtos.CATEGORIES.contains(r.category())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category");
        }
        if (profanityFilter.containsProfanity(r.title()) || profanityFilter.containsProfanity(r.description())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The listing contains inappropriate language.");
        }
    }

    private static void apply(Listing l, ListingRequest r) {
        l.setTitle(r.title().trim());
        l.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        l.setPriceRappen(r.priceRappen() == null || r.priceRappen() == 0 ? null : r.priceRappen());
        l.setCategory(r.category());
    }

    /** A listing I may look at: my own, or someone else's that is up and not blocked. */
    private Listing viewable(Long id, User me) {
        Listing l = listings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        boolean mine = l.getSeller().getId().equals(me.getId());
        if (!mine && (Boolean.TRUE.equals(l.getHiddenByReport())
                || blockRepository.existsBlockBetween(me.getId(), l.getSeller().getId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found");
        }
        return l;
    }

    private Listing owned(Long id, User me) {
        Listing l = listings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        if (!l.getSeller().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your listing");
        }
        return l;
    }

    private ListingConversation participantOf(Long id, User me) {
        ListingConversation c = conversations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        boolean involved = c.getBuyer().getId().equals(me.getId())
                || c.getListing().getSeller().getId().equals(me.getId());
        if (!involved) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        return c;
    }

    private static User other(ListingConversation c, User me) {
        return c.getBuyer().getId().equals(me.getId()) ? c.getListing().getSeller() : c.getBuyer();
    }

    private void markRead(ListingConversation c, User me) {
        if (c.getBuyer().getId().equals(me.getId())) c.setBuyerReadAt(Instant.now());
        else c.setSellerReadAt(Instant.now());
        conversations.save(c);
    }

    private ListingResponse toResponse(Listing l, User me) {
        boolean mine = l.getSeller().getId().equals(me.getId());
        List<PhotoResponse> ps = photos.findByListingOrderBySortOrderAsc(l).stream()
                .map(p -> new PhotoResponse(p.getId(),
                        photoService.url(p.getStorageKey(), PhotoRendition.FEED),
                        photoService.url(p.getStorageKey(), PhotoRendition.THUMB)))
                .toList();
        double distance = me.getLatitude() == null || l.getLatitude() == null ? -1
                : Math.round(GeoUtil.distanceKm(me.getLatitude(), me.getLongitude(), l.getLatitude(), l.getLongitude()));
        Long myConversation = mine ? null
                : conversations.findByListingAndBuyer(l, me).map(ListingConversation::getId).orElse(null);
        int asked = mine ? (int) conversations.findByListing(l).stream().filter(c -> c.getLastMessageAt() != null).count() : 0;
        return new ListingResponse(l.getId(), l.getTitle(), l.getDescription(), l.getPriceRappen(), l.getCategory(),
                l.getStatus(), ps, l.getSeller().getId(), l.getSeller().getName(),
                photoService.url(l.getSeller().getProfilePictureKey(), PhotoRendition.THUMB),
                distance, l.getCreatedAt(), mine, myConversation, asked);
    }

    private ConversationSummary summary(ListingConversation c, User me) {
        User other = other(c, me);
        boolean seller = c.getListing().getSeller().getId().equals(me.getId());
        Instant readAt = seller ? c.getSellerReadAt() : c.getBuyerReadAt();
        var last = messages.findFirstByConversationOrderBySentAtDesc(c);
        boolean unread = last.isPresent() && !last.get().getSender().getId().equals(me.getId())
                && (readAt == null || readAt.isBefore(last.get().getSentAt()));
        String photo = photos.findByListingOrderBySortOrderAsc(c.getListing()).stream().findFirst()
                .map(p -> photoService.url(p.getStorageKey(), PhotoRendition.THUMB)).orElse(null);
        return new ConversationSummary(c.getId(), c.getListing().getId(), c.getListing().getTitle(), photo,
                c.getListing().getStatus(), other.getId(), other.getName(),
                photoService.url(other.getProfilePictureKey(), PhotoRendition.THUMB), seller,
                last.map(ListingMessage::getContent).orElse(null), c.getLastMessageAt(), unread);
    }

    private static MessageResponse toMessage(ListingMessage m) {
        return new MessageResponse(m.getId(), m.getConversation().getId(), m.getSender().getId(), m.getContent(), m.getSentAt());
    }

    private Set<Long> blockedIds(User me) {
        Set<Long> ids = new HashSet<>(blockRepository.findBlockedIdsByBlockerId(me.getId()));
        ids.addAll(blockRepository.findBlockerIdsByBlockedId(me.getId()));
        return ids;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
