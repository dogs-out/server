package com.dogsout.server.sos;

import com.dogsout.server.GeoUtil;
import com.dogsout.server.ProfanityFilter;
import com.dogsout.server.dog.Dog;
import com.dogsout.server.dog.DogRepository;
import com.dogsout.server.dog.DogService;
import com.dogsout.server.matching.Match;
import com.dogsout.server.matching.MatchRepository;
import com.dogsout.server.matching.MatchStatus;
import com.dogsout.server.moderation.BlockRepository;
import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.photo.PhotoRendition;
import com.dogsout.server.photo.PhotoService;
import com.dogsout.server.sos.SosDtos.AlertResponse;
import com.dogsout.server.sos.SosDtos.ContactOwnerResponse;
import com.dogsout.server.sos.SosDtos.RaiseAlertRequest;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import com.dogsout.server.websocket.ChatSocketEvent;
import com.dogsout.server.websocket.ChatSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lost-dog alerts.
 *
 * <p>The reach is deliberately wide — {@value #RADIUS_KM} km, twice the discovery radius — because
 * a scared dog covers ground and whoever spots it is rarely a match. That is exactly why the rest
 * is deliberately narrow: one open alert per owner, a weekly cap, and a close that tells everyone.
 * The first false alarm that lingers teaches people to mute the whole thing.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SosService {

    public static final double RADIUS_KM = 100;
    /** An alert nobody closes stops being shown after this long. */
    public static final int OPEN_DAYS = 7;
    /** Alerts one owner may raise per week, counting closed ones. */
    public static final int MAX_PER_WEEK = 3;

    private final LostDogAlertRepository alertRepository;
    private final UserRepository userRepository;
    private final DogRepository dogRepository;
    private final DogService dogService;
    private final MatchRepository matchRepository;
    private final BlockRepository blockRepository;
    private final ProfanityFilter profanityFilter;
    private final PhotoService photoService;
    private final PushNotificationService pushNotificationService;
    private final ChatSocketHandler chatSocketHandler;

    public AlertResponse raise(String email, RaiseAlertRequest request) {
        User me = findUser(email);
        Dog dog = dogRepository.findById(request.dogId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dog not found"));
        if (!dog.getOwner().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only raise an alert for your own dog");
        }
        if (request.note() != null && profanityFilter.containsProfanity(request.note())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your message contains inappropriate language.");
        }
        Instant now = Instant.now();
        if (!alertRepository.findByOwnerAndClosedAtIsNullAndCreatedAtAfter(me, openSince(now)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have an open alert. Close it before raising a new one.");
        }
        if (alertRepository.countByOwnerAndCreatedAtAfter(me, now.minus(Duration.ofDays(7))) >= MAX_PER_WEEK) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You have raised " + MAX_PER_WEEK + " alerts this week. Please contact us if your dog is still missing.");
        }

        LostDogAlert alert = new LostDogAlert();
        alert.setOwner(me);
        alert.setDog(dog);
        alert.setLatitude(request.latitude());
        alert.setLongitude(request.longitude());
        alert.setPlaceName(trimmedOrNull(request.placeName()));
        alert.setNote(trimmedOrNull(request.note()));
        LostDogAlert saved = alertRepository.save(alert);

        String where = saved.getPlaceName() != null ? "Last seen near " + saved.getPlaceName() + ". " : "";
        int notified = notifyArea(saved, "🚨 Lost dog nearby: " + dog.getName(),
                where + "Tap to see photos and help " + me.getName() + " find them.", "SOS_ALERT");
        log.info("SOS alert {} for dog {} notified {} user(s)", saved.getId(), dog.getId(), notified);
        return toResponse(saved, me, now);
    }

    /** Open alerts within reach of the viewer's saved location, plus their own. */
    @Transactional(readOnly = true)
    public List<AlertResponse> nearby(String email) {
        User me = findUser(email);
        Instant now = Instant.now();
        return alertRepository.findByClosedAtIsNullAndCreatedAtAfter(openSince(now)).stream()
                .filter(a -> isMine(a, me) || (withinReach(a, me) && !blocked(a, me)))
                .sorted(Comparator.comparing(LostDogAlert::getCreatedAt).reversed())
                .map(a -> toResponse(a, me, now))
                .toList();
    }

    /**
     * One alert, open or closed. Not limited by distance: it is reached from a push, and
     * a push has to open — the recipient may have moved since it was sent.
     */
    @Transactional(readOnly = true)
    public AlertResponse get(String email, Long id) {
        User me = findUser(email);
        LostDogAlert alert = findAlert(id);
        if (!isMine(alert, me) && blocked(alert, me)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found");
        }
        return toResponse(alert, me, Instant.now());
    }

    public AlertResponse close(String email, Long id, boolean found) {
        User me = findUser(email);
        LostDogAlert alert = findAlert(id);
        if (!isMine(alert, me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can close this alert");
        }
        Instant now = Instant.now();
        if (alert.getClosedAt() == null) {
            alert.setClosedAt(now);
            alert.setFound(found);
            alertRepository.save(alert);
            // Everyone who was asked to look is told they can stop. Without this the
            // alert lives on in their heads and their notification history.
            if (found) {
                notifyArea(alert, "🎉 " + alert.getDog().getName() + " is home!",
                        "Thank you for keeping an eye out.", "SOS_FOUND");
            }
        }
        return toResponse(alert, me, now);
    }

    /**
     * Opens a chat with the owner, so whoever spotted the dog can say where. Any existing
     * row between the two is reused and promoted to MATCHED, the same way a dogsitting
     * contact works — an owner with a lost dog wants to hear from strangers.
     */
    public ContactOwnerResponse contactOwner(String email, Long id) {
        User me = findUser(email);
        LostDogAlert alert = findAlert(id);
        User owner = alert.getOwner();
        if (isMine(alert, me)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This is your own alert");
        }
        if (blocked(alert, me)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found");
        }
        if (!alert.isOpen(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This alert is closed");
        }

        Optional<Match> existing = matchRepository.findFirstByUser1AndUser2OrderByIdAsc(me, owner)
                .or(() -> matchRepository.findFirstByUser1AndUser2OrderByIdAsc(owner, me));
        Match match = existing.orElseGet(() -> {
            Match m = new Match();
            m.setUser1(me);
            m.setUser2(owner);
            return m;
        });
        if (match.getStatus() != MatchStatus.MATCHED) {
            match.setStatus(MatchStatus.MATCHED);
            match = matchRepository.save(match);
            chatSocketHandler.sendToUser(owner.getId(), ChatSocketEvent.newMatch(match.getId()));
        }
        return new ContactOwnerResponse(match.getId());
    }

    /** Pushes to everyone within reach of the alert except its owner and anyone blocked either way. */
    private int notifyArea(LostDogAlert alert, String title, String body, String type) {
        int sent = 0;
        for (User user : userRepository.findAll()) {
            if (user.getId().equals(alert.getOwner().getId())) continue;
            if (!pushNotificationService.canReceive(user)) continue;
            if (!withinReach(alert, user) || blocked(alert, user)) continue;
            pushNotificationService.send(user, title, body, Map.of("type", type, "alertId", alert.getId()));
            sent++;
        }
        return sent;
    }

    private boolean withinReach(LostDogAlert alert, User user) {
        Double km = distanceKm(alert, user);
        return km != null && km <= RADIUS_KM;
    }

    private static Double distanceKm(LostDogAlert alert, User user) {
        if (user.getLatitude() == null || user.getLongitude() == null) return null;
        return GeoUtil.distanceKm(alert.getLatitude(), alert.getLongitude(), user.getLatitude(), user.getLongitude());
    }

    private boolean blocked(LostDogAlert alert, User user) {
        return blockRepository.existsBlockBetween(alert.getOwner().getId(), user.getId());
    }

    private static boolean isMine(LostDogAlert alert, User me) {
        return alert.getOwner().getId().equals(me.getId());
    }

    private static Instant openSince(Instant now) {
        return now.minus(Duration.ofDays(OPEN_DAYS));
    }

    private AlertResponse toResponse(LostDogAlert a, User viewer, Instant now) {
        User owner = a.getOwner();
        Double km = distanceKm(a, viewer);
        return new AlertResponse(
                a.getId(),
                dogService.getDog(a.getDog().getId()),
                owner.getId(),
                owner.getName(),
                photoService.url(owner.getProfilePictureKey(), PhotoRendition.THUMB),
                a.getLatitude(),
                a.getLongitude(),
                a.getPlaceName(),
                a.getNote(),
                a.getCreatedAt(),
                a.isOpen(now),
                Boolean.TRUE.equals(a.getFound()),
                isMine(a, viewer),
                km != null ? Math.round(km * 10) / 10.0 : null);
    }

    private LostDogAlert findAlert(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static String trimmedOrNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
