package com.dogsout.server.sitter;

import com.dogsout.server.matching.Match;
import com.dogsout.server.matching.MatchRepository;
import com.dogsout.server.matching.MatchStatus;
import com.dogsout.server.moderation.BlockRepository;
import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import com.dogsout.server.websocket.ChatSocketEvent;
import com.dogsout.server.websocket.ChatSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dogsout.server.GeoUtil;
import com.dogsout.server.dog.Dog;
import com.dogsout.server.photo.PhotoRendition;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class SitterService {

    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final BlockRepository blockRepository;
    private final ChatSocketHandler chatSocketHandler;
    private final PushNotificationService pushNotificationService;
    private final SittingRequestRepository sittingRequestRepository;
    private final com.dogsout.server.dog.DogRepository dogRepository;
    private final com.dogsout.server.photo.PhotoService photoService;
    private final SitterReviewRepository sitterReviewRepository;
    private final com.dogsout.server.chat.MessageRepository messageRepository;
    private final SitterCancellationRepository sitterCancellationRepository;

    /** Ids are stored joined, the same shape the tag columns use. */
    private static final String ID_SEPARATOR = "\\|\\|";

    /**
     * Posts a request for a sitter.
     *
     * <p>Only the owner's own dogs, and only a window that has not already passed:
     * a job nobody can still take is noise in everybody else's list.
     */
    public SittingRequestResponse createRequest(String email, CreateSittingRequest request) {
        User me = findUser(email);

        validateWindow(request, me);

        SittingRequest saved = new SittingRequest();
        saved.setOwner(me);
        saved.setStartsAt(request.startsAt());
        saved.setEndsAt(request.endsAt());
        saved.setDogIds(joinIds(request.dogIds()));
        saved.setNote(trimmedOrNull(request.note()));
        saved.setStatus(SittingRequestStatus.OPEN);
        return toResponse(sittingRequestRepository.save(saved), me);
    }

    /**
     * The open jobs a sitter can still take.
     *
     * <p>Past windows are left out rather than shown greyed: a list of jobs that
     * cannot be taken teaches people to stop reading it. An accepted job is gone
     * from here too — accepting sets the status, and only OPEN is listed.
     */
    public List<SittingRequestResponse> openRequests(String email) {
        User me = findUser(email);
        return sittingRequestRepository
                .findByStatusAndEndsAtAfterOrderByStartsAtAsc(SittingRequestStatus.OPEN, Instant.now())
                .stream()
                .filter(r -> !blockRepository.existsBlockBetween(me.getId(), r.getOwner().getId()))
                .map(r -> toResponse(r, me))
                .toList();
    }

    /** What this account has posted, open or closed, so it can be managed. */
    public List<SittingRequestResponse> myRequests(String email) {
        User me = findUser(email);
        return sittingRequestRepository.findByOwnerOrderByStartsAtAsc(me).stream()
                .map(r -> toResponse(r, me))
                .toList();
    }

    /** Closes a request. Only the owner, and closing twice is not an error. */
    public SittingRequestResponse closeRequest(String email, Long id) {
        User me = findUser(email);
        SittingRequest found = sittingRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (!found.getOwner().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your request");
        }
        found.setStatus(SittingRequestStatus.CLOSED);
        return toResponse(sittingRequestRepository.save(found), me);
    }

    /**
     * Changes a request that has not been taken yet.
     *
     * <p>Editing stops once a sitter has been accepted: they agreed to a particular
     * evening with particular dogs, and silently moving it underneath them is how
     * somebody ends up at an empty flat. Cancel and repost instead — which costs
     * the owner a minute and tells the sitter something changed.
     */
    public SittingRequestResponse updateRequest(String email, Long id, CreateSittingRequest request) {
        User me = findUser(email);
        SittingRequest found = ownedRequest(me, id);

        if (found.isAccepted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Someone has already taken this job. Cancel it and post a new one.");
        }
        if (found.isOver(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That window is already over");
        }
        validateWindow(request, me);

        found.setStartsAt(request.startsAt());
        found.setEndsAt(request.endsAt());
        found.setDogIds(joinIds(request.dogIds()));
        found.setNote(trimmedOrNull(request.note()));
        return toResponse(sittingRequestRepository.save(found), me);
    }

    /**
     * A sitter offering to take a job.
     *
     * <p>The offer is a chat message, not a row in a table of applications. Two
     * reasons: the owner is choosing a person to leave their dog with, so the
     * conversation is the part that matters and it should start immediately; and
     * an offer that lives in a list somewhere is an offer nobody answers. The
     * message carries the request id, which is all the owner's side needs to show
     * an Accept button on that bubble.
     */
    public ContactSitterResponse offer(String email, Long requestId, String message) {
        User me = findUser(email);
        SittingRequest job = sittingRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        if (job.getOwner().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That is your own request");
        }
        if (job.getStatus() != SittingRequestStatus.OPEN || job.isOver(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This job is no longer open");
        }
        if (me.isSitterBlocked()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Dogsitting is paused on your account after repeated late cancellations.");
        }

        ContactSitterResponse chat = contact(email, job.getOwner().getId());

        com.dogsout.server.chat.Message offer = new com.dogsout.server.chat.Message();
        offer.setSender(me);
        offer.setReceiver(job.getOwner());
        offer.setMatch(matchRepository.findById(chat.matchId()).orElseThrow());
        offer.setContent(trimmedOrNull(message) == null
                ? "I can take this sitting job." : message.trim());
        offer.setSittingRequestId(job.getId());
        messageRepository.save(offer);

        pushNotificationService.send(job.getOwner(), me.getName() + " can sit for you 🐾",
                "Tap to read the offer and accept it.",
                java.util.Map.of("type", "SITTING_OFFER", "matchId", chat.matchId(),
                        "otherUserId", me.getId(), "name", me.getName()));
        return chat;
    }

    /**
     * The owner picking one of the sitters who offered.
     *
     * <p>Accepting closes the job, which is what takes it off everybody else's
     * board — see the note on {@link SittingRequest#getStatus()} for why that is a
     * status plus a sitter rather than a third status value.
     */
    public SittingRequestResponse accept(String email, Long id, Long sitterId) {
        User me = findUser(email);
        SittingRequest job = ownedRequest(me, id);

        if (job.isAccepted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already accepted someone for this job");
        }
        if (job.isOver(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That window is already over");
        }
        User sitter = userRepository.findById(sitterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (sitter.getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot sit for yourself");
        }

        job.setSitter(sitter);
        job.setAcceptedAt(Instant.now());
        job.setStatus(SittingRequestStatus.CLOSED);
        SittingRequest saved = sittingRequestRepository.save(job);

        pushNotificationService.send(sitter, "You got the job 🎉",
                me.getName() + " accepted your offer to sit.",
                java.util.Map.of("type", "SITTING_ACCEPTED", "requestId", saved.getId()));
        return toResponse(saved, me);
    }

    /**
     * The sitter pulling out of a job they had taken.
     *
     * <p>The job goes back on the board rather than dying, so the owner has a
     * chance of finding somebody else instead of only being told bad news. If the
     * window has already passed there is nothing to reopen and it simply closes.
     *
     * <p>Cancelling early is not penalised at all. A sitter who realises in good
     * time that they cannot make it is doing the right thing, and a rule that
     * punished it would teach people to say nothing until the last moment —
     * which is the failure this is meant to prevent.
     */
    public SittingRequestResponse cancelAsSitter(String email, Long id, String reason) {
        User me = findUser(email);
        SittingRequest job = sittingRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        if (job.getSitter() == null || !job.getSitter().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the sitter for this job");
        }
        Instant now = Instant.now();
        if (job.isOver(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That sitting is already over");
        }

        boolean late = job.getStartsAt() != null
                && job.getStartsAt().isBefore(now.plus(SitterCancellation.LATE_HOURS, ChronoUnit.HOURS));

        SitterCancellation strike = new SitterCancellation();
        strike.setSitter(me);
        strike.setRequestId(job.getId());
        strike.setLate(late);
        strike.setSittingStartsAt(job.getStartsAt());
        sitterCancellationRepository.save(strike);

        User owner = job.getOwner();
        job.setSitter(null);
        job.setAcceptedAt(null);
        job.setStatus(SittingRequestStatus.OPEN);
        SittingRequest saved = sittingRequestRepository.save(job);

        if (late) applyPenaltyIfEarned(me);

        String tail = reason == null || reason.isBlank() ? "" : " — \"" + reason.trim() + "\"";
        pushNotificationService.send(owner, me.getName() + " cancelled the sitting",
                "Your request is open again so another sitter can take it." + tail,
                java.util.Map.of("type", "SITTING_CANCELLED", "requestId", saved.getId()));

        return toResponse(saved, owner);
    }

    /**
     * Suspends the right to take jobs once the late cancellations pile up.
     *
     * <p>Counted over a rolling year and measured from now, so the penalty ends by
     * itself. Nothing switches the sitter's own toggle off — the role is
     * suspended, not decided for them, and it comes back without anybody having
     * to remember to restore it.
     */
    private void applyPenaltyIfEarned(User sitter) {
        Instant yearAgo = Instant.now().minus(365, ChronoUnit.DAYS);
        long strikes = sitterCancellationRepository
                .countBySitterAndLateIsTrueAndCreatedAtAfter(sitter, yearAgo);
        if (strikes < SitterCancellation.STRIKES) return;

        sitter.setSitterBlockedUntil(Instant.now().plus(SitterCancellation.PENALTY_DAYS, ChronoUnit.DAYS));
        userRepository.save(sitter);
        pushNotificationService.send(sitter, "Dogsitting paused",
                "Three late cancellations in a year — you can take jobs again in "
                        + SitterCancellation.PENALTY_DAYS + " days.",
                java.util.Map.of("type", "SITTING_BLOCKED"));
    }

    /** What the app needs to warn a sitter before a cancellation costs them. */
    public SitterStandingResponse standing(String email) {
        User me = findUser(email);
        Instant yearAgo = Instant.now().minus(365, ChronoUnit.DAYS);
        return new SitterStandingResponse(
                sitterCancellationRepository.countBySitterAndLateIsTrueAndCreatedAtAfter(me, yearAgo),
                SitterCancellation.STRIKES,
                SitterCancellation.LATE_HOURS,
                me.isSitterBlocked() ? me.getSitterBlockedUntil() : null);
    }

    /**
     * The owner handing over what the sitter needs to actually do the job.
     *
     * <p>Asked for only once somebody is coming: most requests are never taken,
     * and nobody types their address and their vet's number into a form on the
     * chance that one might be. Saving posts it into the chat as a card, so it
     * sits where the sitter will look for it rather than behind a screen they
     * would have to remember exists.
     */
    public SittingRequestResponse shareDetails(String email, Long id, SittingDetailsRequest details) {
        User me = findUser(email);
        SittingRequest job = ownedRequest(me, id);

        if (job.getSitter() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Accept a sitter before sharing the details");
        }

        boolean resend = job.getDetailsSharedAt() != null;
        job.setTodoList(trimmedOrNull(details.todoList()));
        job.setEmergencyPhone(trimmedOrNull(details.emergencyPhone()));
        job.setAddressLabel(trimmedOrNull(details.addressLabel()));
        job.setAddressLatitude(details.addressLatitude());
        job.setAddressLongitude(details.addressLongitude());
        job.setDetailsSharedAt(Instant.now());
        SittingRequest saved = sittingRequestRepository.save(job);

        User sitter = job.getSitter();
        ContactSitterResponse chat = contact(email, sitter.getId());
        com.dogsout.server.chat.Message card = new com.dogsout.server.chat.Message();
        card.setSender(me);
        card.setReceiver(sitter);
        card.setMatch(matchRepository.findById(chat.matchId()).orElseThrow());
        // The text is the fallback for any client too old to draw the card, so it
        // says the useful thing rather than "shared details".
        card.setContent(resend
                ? "I updated the sitting details."
                : "Here are the details for the sitting.");
        card.setSittingRequestId(saved.getId());
        card.setSittingDetails(true);
        messageRepository.save(card);

        pushNotificationService.send(sitter,
                resend ? me.getName() + " updated the sitting details"
                       : me.getName() + " sent the sitting details",
                "Address, to-do list and emergency number are in your chat.",
                java.util.Map.of("type", "SITTING_DETAILS", "matchId", chat.matchId(),
                        "otherUserId", me.getId(), "name", me.getName()));

        return toResponse(saved, me);
    }

    /** Jobs a sitter was accepted for, so their own tab shows what they committed to. */
    public List<SittingRequestResponse> myJobsAsSitter(String email) {
        User me = findUser(email);
        return sittingRequestRepository.findBySitterOrderByStartsAtAsc(me).stream()
                .map(r -> toResponse(r, me))
                .toList();
    }

    private SittingRequest ownedRequest(User me, Long id) {
        SittingRequest found = sittingRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (!found.getOwner().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your request");
        }
        return found;
    }

    private void validateWindow(CreateSittingRequest request, User me) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The end has to come after the start");
        }
        if (request.endsAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That window is already over");
        }
        if (request.dogIds() == null || request.dogIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick at least one dog");
        }
        List<Long> mine = dogRepository.findByOwner(me).stream().map(Dog::getId).toList();
        if (!mine.containsAll(request.dogIds())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only ask for your own dogs");
        }
    }

    private static String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining("||"));
    }

    private static String trimmedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SittingRequestResponse toResponse(SittingRequest request, User viewer) {
        User owner = request.getOwner();
        List<Long> ids = request.getDogIds() == null || request.getDogIds().isBlank()
                ? List.of()
                : Arrays.stream(request.getDogIds().split(ID_SEPARATOR)).map(Long::valueOf).toList();
        // By name rather than by id: the list is read, not joined against.
        List<String> dogs = dogRepository.findAllById(ids).stream().map(Dog::getName).toList();

        double distance = viewer.getLatitude() == null || owner.getLatitude() == null
                ? -1
                : Math.round(GeoUtil.distanceKm(
                        viewer.getLatitude(), viewer.getLongitude(),
                        owner.getLatitude(), owner.getLongitude()));

        boolean mine = owner.getId().equals(viewer.getId());
        boolean over = request.isOver(Instant.now());
        User sitter = request.getSitter();
        boolean forInvolved = mine || (sitter != null && sitter.getId().equals(viewer.getId()));

        // Only the owner is ever asked to rate, and only once, and only when
        // somebody actually sat — a job closed with nobody found has nothing to say.
        boolean awaitingReview = mine && over && sitter != null
                && !sitterReviewRepository.existsByRequest(request);

        return new SittingRequestResponse(
                request.getId(), owner.getId(), owner.getName(),
                photoService.url(owner.getProfilePictureKey(), PhotoRendition.THUMB),
                request.getStartsAt(), request.getEndsAt(), dogs, ids, request.getNote(),
                request.getStatus().name(),
                mine,
                sitter == null ? null : sitter.getId(),
                sitter == null ? null : sitter.getName(),
                sitter == null ? null : photoService.url(sitter.getProfilePictureKey(), PhotoRendition.THUMB),
                over,
                mine && !over && sitter == null && request.getStatus() == SittingRequestStatus.OPEN,
                awaitingReview,
                distance,
                // Somebody's address, phone number and house keys routine. Only the
                // owner and the sitter who was actually accepted ever see these —
                // the job board is public to every sitter in range.
                forInvolved ? request.getTodoList() : null,
                forInvolved ? request.getEmergencyPhone() : null,
                forInvolved ? request.getAddressLabel() : null,
                forInvolved ? request.getAddressLatitude() : null,
                forInvolved ? request.getAddressLongitude() : null,
                request.getDetailsSharedAt() != null);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    /**
     * Dogsitting contact opens a chat immediately in either direction — a sitter
     * reaching an owner from the seeker pool, or an owner reaching a sitter from the
     * available-sitters pool. Both sides opted into their pool, so no mutual like is
     * required. Chat is authorised through MATCHED matches, so this finds-or-creates
     * a Match row with that status.
     */
    public ContactSitterResponse contact(String email, Long targetUserId) {
        User me = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (me.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot contact yourself");
        }
        if (!Boolean.TRUE.equals(me.getIsSitter()) && !Boolean.TRUE.equals(me.getLookingForSitter())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Enable a dogsitting role to contact people");
        }
        if (blockRepository.existsBlockBetween(me.getId(), target.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This user is not available");
        }

        // Reuse any existing row in either direction — the unique constraint on
        // (user1, user2) forbids a second one, and an existing MATCHED row already
        // carries the chat history.
        Optional<Match> existing = matchRepository.findFirstByUser1AndUser2OrderByIdAsc(me, target)
                .or(() -> matchRepository.findFirstByUser1AndUser2OrderByIdAsc(target, me));

        if (existing.isPresent()) {
            Match match = existing.get();
            if (match.getStatus() == MatchStatus.MATCHED) {
                return new ContactSitterResponse(match.getId());
            }
            // An earlier one-sided like or pass is superseded by the sitter contact
            match.setStatus(MatchStatus.MATCHED);
            matchRepository.save(match);
            notifyTarget(me, target, match.getId());
            return new ContactSitterResponse(match.getId());
        }

        // Checked only before opening a *new* chat: an existing match stays reachable
        // even if the other side has since switched their dogsitting toggles off.
        boolean sitterToOwner = Boolean.TRUE.equals(me.getIsSitter())
                && Boolean.TRUE.equals(target.getLookingForSitter());
        boolean ownerToSitter = Boolean.TRUE.equals(me.getLookingForSitter())
                && Boolean.TRUE.equals(target.getIsSitter());
        if (!sitterToOwner && !ownerToSitter) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This user is not available for dogsitting");
        }

        Match match = new Match();
        match.setUser1(me);
        match.setUser2(target);
        match.setStatus(MatchStatus.MATCHED);
        Match saved = matchRepository.save(match);
        notifyTarget(me, target, saved.getId());
        return new ContactSitterResponse(saved.getId());
    }

    private void notifyTarget(User me, User target, Long matchId) {
        chatSocketHandler.sendToUser(target.getId(), ChatSocketEvent.newMatch(matchId));
        if (!chatSocketHandler.isOnline(target.getId())) {
            pushNotificationService.send(target, "New sitter contact 🐾",
                    me.getName() + " is available to sit for your dog. Say hi!",
                    java.util.Map.of("type", "NEW_MATCH", "matchId", matchId));
        }
    }
}
