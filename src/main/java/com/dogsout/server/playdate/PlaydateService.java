package com.dogsout.server.playdate;

import com.dogsout.server.GeoUtil;
import com.dogsout.server.ProfanityFilter;
import com.dogsout.server.matching.MatchRepository;
import com.dogsout.server.moderation.BlockRepository;
import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.playdate.PlaydateDtos.*;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import com.dogsout.server.websocket.ChatSocketEvent;
import com.dogsout.server.websocket.ChatSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class PlaydateService {

    private static final double DEFAULT_MAX_DISTANCE_KM = 50.0;
    // Group chat stays open a while after the start so people can coordinate at the park
    private static final Duration CHAT_GRACE = Duration.ofHours(6);

    private final PlaydateRepository playdateRepository;
    private final PlaydateParticipantRepository participantRepository;
    private final PlaydateMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final BlockRepository blockRepository;
    private final ProfanityFilter profanityFilter;
    private final ChatSocketHandler chatSocketHandler;
    private final PushNotificationService pushNotificationService;

    // ─── Feed & detail ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PlaydateResponse> getFeed(String email) {
        User me = findUser(email);
        Set<Long> blocked = blockedUserIds(me);
        Set<Long> matchedIds = matchedUserIds(me);
        Set<Long> minePlaydateIds = new HashSet<>();
        participantRepository.findByUser(me).forEach(p -> minePlaydateIds.add(p.getPlaydate().getId()));

        boolean hasLocation = me.getLatitude() != null && me.getLongitude() != null;
        double maxDist = me.getMaxDistanceKm() != null ? me.getMaxDistanceKm() : DEFAULT_MAX_DISTANCE_KM;

        return playdateRepository.findByStatusAndStartsAtAfter(PlaydateStatus.ACTIVE, Instant.now()).stream()
                .filter(p -> !blocked.contains(p.getHost().getId()))
                .filter(p -> {
                    if (p.getHost().getId().equals(me.getId())) return true;
                    if (minePlaydateIds.contains(p.getId())) return true;
                    return switch (p.getVisibility()) {
                        case MATCHES_ONLY -> matchedIds.contains(p.getHost().getId());
                        case PUBLIC -> hasLocation
                                && GeoUtil.distanceKm(me.getLatitude(), me.getLongitude(),
                                        p.getLatitude(), p.getLongitude()) <= maxDist;
                        case INVITE_ONLY -> false; // only via participant row, handled above
                    };
                })
                .sorted(Comparator.comparing(Playdate::getStartsAt))
                .map(p -> toResponse(p, me, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlaydateResponse getPlaydate(String email, Long id) {
        User me = findUser(email);
        Playdate playdate = findViewablePlaydate(id, me);
        return toResponse(playdate, me, true);
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    public PlaydateResponse create(String email, CreatePlaydateRequest request) {
        User me = findUser(email);
        validateDetails(request.title(), request.description(), request.startsAt(), request.maxParticipants());

        Playdate playdate = new Playdate();
        playdate.setHost(me);
        applyDetails(playdate, request.title(), request.description(), request.parkName(),
                request.address(), request.latitude(), request.longitude(),
                request.startsAt(), request.maxParticipants());
        playdate.setVisibility(request.visibility());
        playdate.setStatus(PlaydateStatus.ACTIVE);
        Playdate saved = playdateRepository.save(playdate);

        if (request.visibility() == PlaydateVisibility.INVITE_ONLY && request.inviteUserIds() != null) {
            inviteInternal(saved, me, request.inviteUserIds());
        }
        return toResponse(saved, me, true);
    }

    public PlaydateResponse update(String email, Long id, UpdatePlaydateRequest request) {
        User me = findUser(email);
        Playdate playdate = findHostedPlaydate(id, me);
        if (playdate.getStatus() == PlaydateStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This playdate was cancelled");
        }
        validateDetails(request.title(), request.description(), request.startsAt(), request.maxParticipants());
        applyDetails(playdate, request.title(), request.description(), request.parkName(),
                request.address(), request.latitude(), request.longitude(),
                request.startsAt(), request.maxParticipants());
        playdateRepository.save(playdate);
        notifyMembers(playdate, me, ParticipantStatus.JOINED, "Playdate updated 🐾",
                hostLabel(playdate) + " changed the details of \"" + displayName(playdate) + "\"",
                "PLAYDATE_UPDATED");
        return toResponse(playdate, me, true);
    }

    public void cancel(String email, Long id) {
        User me = findUser(email);
        Playdate playdate = findHostedPlaydate(id, me);
        if (playdate.getStatus() == PlaydateStatus.CANCELLED) return;
        playdate.setStatus(PlaydateStatus.CANCELLED);
        playdateRepository.save(playdate);
        notifyMembers(playdate, me, null, "Playdate cancelled",
                "\"" + displayName(playdate) + "\" was cancelled by " + hostLabel(playdate),
                "PLAYDATE_CANCELLED");
    }

    // ─── Membership ───────────────────────────────────────────────────────────

    public PlaydateResponse join(String email, Long id) {
        User me = findUser(email);
        Playdate playdate = findViewablePlaydate(id, me);
        if (playdate.getHost().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You are hosting this playdate");
        }
        requireActiveAndUpcoming(playdate);

        Optional<PlaydateParticipant> existing = participantRepository.findByPlaydateAndUser(playdate, me);
        if (playdate.getVisibility() == PlaydateVisibility.INVITE_ONLY && existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This playdate is invite-only");
        }
        if (existing.isPresent() && existing.get().getStatus() == ParticipantStatus.JOINED) {
            return toResponse(playdate, me, true);
        }
        // The host counts towards the cap
        if (playdate.getMaxParticipants() != null
                && participantRepository.countByPlaydateAndStatus(playdate, ParticipantStatus.JOINED) + 1
                        >= playdate.getMaxParticipants()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Playdate is full");
        }

        PlaydateParticipant participant = existing.orElseGet(() -> {
            PlaydateParticipant p = new PlaydateParticipant();
            p.setPlaydate(playdate);
            p.setUser(me);
            return p;
        });
        participant.setStatus(ParticipantStatus.JOINED);
        participantRepository.save(participant);

        User host = playdate.getHost();
        chatSocketHandler.sendToUser(host.getId(), ChatSocketEvent.playdateUpdated(playdate.getId()));
        if (!chatSocketHandler.isOnline(host.getId())) {
            pushNotificationService.send(host, "New playdate guest 🐾",
                    me.getName() + " joined \"" + displayName(playdate) + "\"",
                    Map.of("type", "PLAYDATE_JOINED", "playdateId", playdate.getId()));
        }
        return toResponse(playdate, me, true);
    }

    public void leave(String email, Long id) {
        User me = findUser(email);
        Playdate playdate = findViewablePlaydate(id, me);
        if (playdate.getHost().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Host must cancel the playdate instead");
        }
        // Deleting an INVITED row is declining the invite; deleting a JOINED row is leaving
        participantRepository.findByPlaydateAndUser(playdate, me).ifPresent(participantRepository::delete);
        chatSocketHandler.sendToUser(playdate.getHost().getId(), ChatSocketEvent.playdateUpdated(playdate.getId()));
    }

    public PlaydateResponse invite(String email, Long id, List<Long> userIds) {
        User me = findUser(email);
        Playdate playdate = findHostedPlaydate(id, me);
        if (playdate.getVisibility() != PlaydateVisibility.INVITE_ONLY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only invite-only playdates take invites");
        }
        requireActiveAndUpcoming(playdate);
        inviteInternal(playdate, me, userIds);
        return toResponse(playdate, me, true);
    }

    // Invitees must be mutual matches of the host; anyone else is silently dropped
    private void inviteInternal(Playdate playdate, User host, List<Long> userIds) {
        Set<Long> matchedIds = matchedUserIds(host);
        for (Long userId : userIds) {
            if (userId == null || userId.equals(host.getId()) || !matchedIds.contains(userId)) continue;
            if (blockRepository.existsBlockBetween(host.getId(), userId)) continue;
            User invitee = userRepository.findById(userId).orElse(null);
            if (invitee == null) continue;
            if (participantRepository.findByPlaydateAndUser(playdate, invitee).isPresent()) continue;
            PlaydateParticipant participant = new PlaydateParticipant();
            participant.setPlaydate(playdate);
            participant.setUser(invitee);
            participant.setStatus(ParticipantStatus.INVITED);
            participantRepository.save(participant);
            chatSocketHandler.sendToUser(userId, ChatSocketEvent.playdateUpdated(playdate.getId()));
            pushNotificationService.send(invitee, "Playdate invite 🐾",
                    host.getName() + " invited you to \"" + displayName(playdate) + "\"",
                    Map.of("type", "PLAYDATE_INVITE", "playdateId", playdate.getId()));
        }
    }

    // ─── Group chat ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PlaydateMessageResponse> getMessages(String email, Long id) {
        User me = findUser(email);
        Playdate playdate = findViewablePlaydate(id, me);
        requireChatMember(playdate, me);
        return messageRepository.findByPlaydateOrderBySentAtAsc(playdate).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    public PlaydateMessageResponse sendMessage(String email, Long id, String content) {
        User me = findUser(email);
        Playdate playdate = findViewablePlaydate(id, me);
        requireChatMember(playdate, me);
        if (playdate.getStatus() == PlaydateStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This playdate was cancelled");
        }
        if (Instant.now().isAfter(playdate.getStartsAt().plus(CHAT_GRACE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This playdate has ended");
        }
        if (profanityFilter.containsProfanity(content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your message contains inappropriate language.");
        }

        PlaydateMessage message = new PlaydateMessage();
        message.setPlaydate(playdate);
        message.setSender(me);
        message.setContent(content);
        PlaydateMessageResponse response = toMessageResponse(messageRepository.save(message));

        for (User member : chatMembers(playdate)) {
            if (member.getId().equals(me.getId())) continue;
            chatSocketHandler.sendToUser(member.getId(), ChatSocketEvent.playdateMessage(playdate.getId(), response));
            if (!chatSocketHandler.isOnline(member.getId())) {
                String preview = content.length() > 80 ? content.substring(0, 77) + "…" : content;
                pushNotificationService.send(member, displayName(playdate),
                        me.getName() + ": " + preview,
                        Map.of("type", "PLAYDATE_MESSAGE", "playdateId", playdate.getId()));
            }
        }
        return response;
    }

    // ─── Account deletion support ─────────────────────────────────────────────

    /** Removes every playdate trace of a user; called from UserService.deleteAccount. */
    public void deleteAllForUser(User user) {
        for (Playdate hosted : playdateRepository.findByHost(user)) {
            messageRepository.deleteByPlaydate(hosted);
            participantRepository.deleteByPlaydate(hosted);
            playdateRepository.delete(hosted);
        }
        messageRepository.deleteBySender(user);
        participantRepository.deleteByUser(user);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Playdate findPlaydate(Long id) {
        return playdateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playdate not found"));
    }

    private Playdate findHostedPlaydate(Long id, User me) {
        Playdate playdate = findPlaydate(id);
        if (!playdate.getHost().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can do this");
        }
        return playdate;
    }

    /**
     * Detail-level visibility: host and participants always; MATCHES_ONLY needs a mutual
     * match with the host; PUBLIC needs nothing more (push deep-links must open without a
     * distance re-check). Blocked-with-host behaves as if the playdate doesn't exist.
     */
    private Playdate findViewablePlaydate(Long id, User me) {
        Playdate playdate = findPlaydate(id);
        if (blockRepository.existsBlockBetween(me.getId(), playdate.getHost().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Playdate not found");
        }
        if (playdate.getHost().getId().equals(me.getId())) return playdate;
        if (participantRepository.findByPlaydateAndUser(playdate, me).isPresent()) return playdate;
        switch (playdate.getVisibility()) {
            case PUBLIC -> { return playdate; }
            case MATCHES_ONLY -> {
                if (matchedUserIds(me).contains(playdate.getHost().getId())) return playdate;
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Playdate not found");
            }
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Playdate not found");
        }
    }

    private void requireActiveAndUpcoming(Playdate playdate) {
        if (playdate.getStatus() == PlaydateStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This playdate was cancelled");
        }
        if (playdate.getStartsAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This playdate has already started");
        }
    }

    private void requireChatMember(Playdate playdate, User me) {
        if (playdate.getHost().getId().equals(me.getId())) return;
        boolean joined = participantRepository.findByPlaydateAndUser(playdate, me)
                .map(p -> p.getStatus() == ParticipantStatus.JOINED)
                .orElse(false);
        if (!joined) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Join the playdate to use its chat");
        }
    }

    private List<User> chatMembers(Playdate playdate) {
        List<User> members = new java.util.ArrayList<>();
        members.add(playdate.getHost());
        participantRepository.findByPlaydate(playdate).stream()
                .filter(p -> p.getStatus() == ParticipantStatus.JOINED)
                .map(PlaydateParticipant::getUser)
                .forEach(members::add);
        return members;
    }

    private void validateDetails(String title, String description, Instant startsAt, Integer maxParticipants) {
        if (startsAt == null || !startsAt.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The playdate must be in the future");
        }
        if (maxParticipants != null && maxParticipants < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The participant limit must be at least 2");
        }
        if (profanityFilter.containsProfanity(title) || profanityFilter.containsProfanity(description)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The playdate contains inappropriate language.");
        }
    }

    private void applyDetails(Playdate playdate, String title, String description, String parkName,
                              String address, Double latitude, Double longitude,
                              Instant startsAt, Integer maxParticipants) {
        playdate.setTitle(title != null && !title.isBlank() ? title.trim() : null);
        playdate.setDescription(description != null && !description.isBlank() ? description.trim() : null);
        playdate.setParkName(parkName.trim());
        playdate.setAddress(address != null && !address.isBlank() ? address.trim() : null);
        playdate.setLatitude(latitude);
        playdate.setLongitude(longitude);
        playdate.setStartsAt(startsAt);
        playdate.setMaxParticipants(maxParticipants);
    }

    private void notifyMembers(Playdate playdate, User except, ParticipantStatus onlyStatus,
                               String title, String body, String pushType) {
        for (PlaydateParticipant participant : participantRepository.findByPlaydate(playdate)) {
            if (onlyStatus != null && participant.getStatus() != onlyStatus) continue;
            User member = participant.getUser();
            if (member.getId().equals(except.getId())) continue;
            chatSocketHandler.sendToUser(member.getId(), ChatSocketEvent.playdateUpdated(playdate.getId()));
            if (!chatSocketHandler.isOnline(member.getId())) {
                pushNotificationService.send(member, title, body,
                        Map.of("type", pushType, "playdateId", playdate.getId()));
            }
        }
    }

    private Set<Long> matchedUserIds(User me) {
        Set<Long> ids = new HashSet<>();
        matchRepository.findAllMatchesForUser(me.getId()).forEach(match -> {
            Long other = match.getUser1().getId().equals(me.getId())
                    ? match.getUser2().getId() : match.getUser1().getId();
            ids.add(other);
        });
        return ids;
    }

    private Set<Long> blockedUserIds(User me) {
        Set<Long> ids = new HashSet<>(blockRepository.findBlockedIdsByBlockerId(me.getId()));
        ids.addAll(blockRepository.findBlockerIdsByBlockedId(me.getId()));
        return ids;
    }

    private String displayName(Playdate playdate) {
        return playdate.getTitle() != null ? playdate.getTitle() : playdate.getParkName();
    }

    private String hostLabel(Playdate playdate) {
        return playdate.getHost().getName();
    }

    private PlaydateResponse toResponse(Playdate playdate, User me, boolean withParticipants) {
        List<PlaydateParticipant> participants = participantRepository.findByPlaydate(playdate);
        int joinedCount = (int) participants.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.JOINED).count() + 1; // + host

        String myStatus;
        if (playdate.getHost().getId().equals(me.getId())) {
            myStatus = "HOST";
        } else {
            myStatus = participants.stream()
                    .filter(p -> p.getUser().getId().equals(me.getId()))
                    .findFirst()
                    .map(p -> p.getStatus().name())
                    .orElse("NONE");
        }

        List<ParticipantResponse> participantResponses = null;
        if (withParticipants) {
            participantResponses = new java.util.ArrayList<>();
            participantResponses.add(new ParticipantResponse(
                    playdate.getHost().getId(), playdate.getHost().getName(),
                    playdate.getHost().getProfilePicture(), "HOST"));
            participants.stream()
                    .sorted(Comparator.comparing(PlaydateParticipant::getCreatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(p -> new ParticipantResponse(p.getUser().getId(), p.getUser().getName(),
                            p.getUser().getProfilePicture(), p.getStatus().name()))
                    .forEach(participantResponses::add);
        }

        // Only members get a preview — a PUBLIC playdate you haven't joined shouldn't
        // leak its chat into your list.
        PlaydateMessage lastMessage = ("HOST".equals(myStatus) || "JOINED".equals(myStatus))
                ? messageRepository.findFirstByPlaydateOrderBySentAtDesc(playdate).orElse(null)
                : null;

        return new PlaydateResponse(
                playdate.getId(),
                playdate.getHost().getId(),
                playdate.getHost().getName(),
                playdate.getHost().getProfilePicture(),
                playdate.getTitle(),
                playdate.getDescription(),
                playdate.getParkName(),
                playdate.getAddress(),
                playdate.getLatitude(),
                playdate.getLongitude(),
                playdate.getStartsAt(),
                playdate.getMaxParticipants(),
                playdate.getVisibility().name(),
                playdate.getStatus().name(),
                joinedCount,
                myStatus,
                participantResponses,
                lastMessage != null ? lastMessage.getContent() : null,
                lastMessage != null ? lastMessage.getSentAt() : null
        );
    }

    private PlaydateMessageResponse toMessageResponse(PlaydateMessage message) {
        return new PlaydateMessageResponse(
                message.getId(),
                message.getSender().getId(),
                message.getSender().getName(),
                message.getSender().getProfilePicture(),
                message.getContent(),
                Objects.requireNonNullElseGet(message.getSentAt(), Instant::now)
        );
    }
}
