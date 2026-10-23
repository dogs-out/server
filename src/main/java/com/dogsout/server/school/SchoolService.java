package com.dogsout.server.school;

import com.dogsout.server.GeoUtil;
import com.dogsout.server.ProfanityFilter;
import com.dogsout.server.auth.EmailService;
import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.photo.PhotoRendition;
import com.dogsout.server.photo.PhotoService;
import com.dogsout.server.school.SchoolDtos.AdminSchoolResponse;
import com.dogsout.server.school.SchoolDtos.EventRequest;
import com.dogsout.server.school.SchoolDtos.EventResponse;
import com.dogsout.server.school.SchoolDtos.ParticipantResponse;
import com.dogsout.server.school.SchoolDtos.PhotoResponse;
import com.dogsout.server.school.SchoolDtos.SchoolRequest;
import com.dogsout.server.school.SchoolDtos.SchoolResponse;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dog schools and their events.
 *
 * <p>A school is shown only once approved; its events likewise. Joining an event
 * is a sign-up, nothing more — the price is paid at the school.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SchoolService {

    public static final String OWNER_SCHOOL = "school";
    static final int MAX_EVENT_PHOTOS = 5;
    private static final double DEFAULT_RADIUS_KM = 50;

    private final DogSchoolRepository schools;
    private final SchoolEventRepository events;
    private final SchoolEventPhotoRepository eventPhotos;
    private final SchoolEventParticipantRepository participants;
    private final UserRepository userRepository;
    private final PhotoService photoService;
    private final ProfanityFilter profanityFilter;
    private final PushNotificationService pushNotificationService;
    private final EmailService emailService;

    @Value("${app.admin-email}")
    private String adminEmail;

    // ─── Schools ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SchoolResponse> nearby(String email) {
        User me = findUser(email);
        return schools.findByStatusOrderByCreatedAtAsc(DogSchool.APPROVED).stream()
                .filter(s -> withinReach(me, s.getLatitude(), s.getLongitude()))
                .map(s -> toSchool(s, me, false))
                .sorted(Comparator.comparingDouble(s -> s.distanceKm() < 0 ? Double.MAX_VALUE : s.distanceKm()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SchoolResponse> mine(String email) {
        User me = findUser(email);
        return schools.findByOwner(me).map(s -> toSchool(s, me, true));
    }

    @Transactional(readOnly = true)
    public SchoolResponse get(String email, Long id) {
        User me = findUser(email);
        return toSchool(visibleSchool(id, me), me, true);
    }

    /** Registers a school; it waits for an admin before anyone else sees it. */
    public SchoolResponse register(String email, SchoolRequest request) {
        User me = findUser(email);
        if (schools.findByOwner(me).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account already has a dog school");
        }
        checkWords(request.name(), request.description(), request.openingHours());
        DogSchool school = new DogSchool();
        school.setOwner(me);
        apply(school, request);
        school = schools.save(school);
        notifyAdmin("New dog school to review: " + school.getName(), describe(school));
        return toSchool(school, me, true);
    }

    /**
     * Edits stay live once approved — a school changing its opening hours should not
     * vanish for days — but the admin is told, so a renamed school is still looked at.
     */
    public SchoolResponse update(String email, SchoolRequest request) {
        User me = findUser(email);
        DogSchool school = schools.findByOwner(me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No dog school"));
        checkWords(request.name(), request.description(), request.openingHours());
        apply(school, request);
        // A rejected school that is fixed goes back into the queue.
        if (DogSchool.REJECTED.equals(school.getStatus())) school.setStatus(DogSchool.PENDING);
        notifyAdmin("Dog school edited: " + school.getName(), describe(school));
        return toSchool(schools.save(school), me, true);
    }

    public SchoolResponse setLogo(String email, MultipartFile file) {
        User me = findUser(email);
        DogSchool school = schools.findByOwner(me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No dog school"));
        String previous = school.getLogoKey();
        school.setLogoKey(photoService.store(OWNER_SCHOOL, file));
        schools.save(school);
        if (previous != null) photoService.delete(previous);
        return toSchool(school, me, true);
    }

    // ─── Admin review ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminSchoolResponse> forReview(String status) {
        return schools.findByStatusOrderByCreatedAtAsc(status).stream()
                .map(s -> new AdminSchoolResponse(toSchool(s, s.getOwner(), true), s.getOwner().getId(),
                        s.getOwner().getName(), s.getOwner().getEmail(), s.getCreatedAt()))
                .toList();
    }

    public void review(Long id, String status, String note) {
        if (!List.of(DogSchool.APPROVED, DogSchool.REJECTED, DogSchool.PENDING).contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status");
        }
        DogSchool school = schools.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such school"));
        school.setStatus(status);
        school.setReviewNote(note == null || note.isBlank() ? null : note.trim());
        schools.save(school);
        if (DogSchool.APPROVED.equals(status)) {
            pushNotificationService.send(school.getOwner(), school.getName() + " is live 🎓",
                    "Your dog school is now visible on Dogs Out. Add your first event!",
                    Map.of("type", "SCHOOL_REVIEWED"));
        } else if (DogSchool.REJECTED.equals(status)) {
            pushNotificationService.send(school.getOwner(), "Your dog school needs a change",
                    "Open the app to see what to fix.", Map.of("type", "SCHOOL_REVIEWED"));
        }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EventResponse> upcomingNearby(String email) {
        User me = findUser(email);
        return events.findByCancelledFalseAndStartsAtAfterOrderByStartsAtAsc(Instant.now()).stream()
                .filter(e -> DogSchool.APPROVED.equals(e.getSchool().getStatus()))
                .filter(e -> withinReach(me, lat(e), lng(e)) || joined(e, me))
                .map(e -> toEvent(e, me))
                .toList();
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(String email, Long id) {
        User me = findUser(email);
        return toEvent(visibleEvent(id, me), me);
    }

    public EventResponse createEvent(String email, EventRequest request) {
        User me = findUser(email);
        DogSchool school = approvedSchoolOf(me);
        validate(request);
        SchoolEvent event = new SchoolEvent();
        event.setSchool(school);
        apply(event, request);
        return toEvent(events.save(event), me);
    }

    public EventResponse updateEvent(String email, Long id, EventRequest request) {
        User me = findUser(email);
        SchoolEvent event = ownedEvent(id, me);
        validate(request);
        apply(event, request);
        events.save(event);
        tellParticipants(event, "Updated: " + event.getTitle(), "Check the new details.");
        return toEvent(event, me);
    }

    public void cancelEvent(String email, Long id) {
        User me = findUser(email);
        SchoolEvent event = ownedEvent(id, me);
        event.setCancelled(true);
        events.save(event);
        tellParticipants(event, "Cancelled: " + event.getTitle(),
                event.getSchool().getName() + " has cancelled this event.");
    }

    public EventResponse addEventPhoto(String email, Long id, MultipartFile file) {
        User me = findUser(email);
        SchoolEvent event = ownedEvent(id, me);
        List<SchoolEventPhoto> existing = eventPhotos.findByEventOrderBySortOrderAsc(event);
        if (existing.size() >= MAX_EVENT_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most " + MAX_EVENT_PHOTOS + " photos");
        }
        SchoolEventPhoto photo = new SchoolEventPhoto();
        photo.setEvent(event);
        photo.setStorageKey(photoService.store(OWNER_SCHOOL, file));
        photo.setSortOrder(existing.size());
        eventPhotos.save(photo);
        return toEvent(event, me);
    }

    public EventResponse deleteEventPhoto(String email, Long id, Long photoId) {
        User me = findUser(email);
        SchoolEvent event = ownedEvent(id, me);
        SchoolEventPhoto photo = eventPhotos.findById(photoId)
                .filter(p -> p.getEvent().getId().equals(event.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
        eventPhotos.delete(photo);
        photoService.delete(photo.getStorageKey());
        return toEvent(event, me);
    }

    public EventResponse join(String email, Long id) {
        User me = findUser(email);
        SchoolEvent event = visibleEvent(id, me);
        if (event.isCancelled() || !event.getStartsAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This event is not taking sign-ups");
        }
        if (event.getSchool().getOwner().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This is your own event");
        }
        if (participants.findByEventAndUser(event, me).isPresent()) return toEvent(event, me);
        if (event.getMaxParticipants() != null && participants.countByEvent(event) >= event.getMaxParticipants()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This event is full");
        }
        SchoolEventParticipant p = new SchoolEventParticipant();
        p.setEvent(event);
        p.setUser(me);
        participants.save(p);
        pushNotificationService.send(event.getSchool().getOwner(), "New sign-up: " + event.getTitle(),
                me.getName() + " signed up.", Map.of("type", "SCHOOL_EVENT_JOINED", "eventId", event.getId()));
        return toEvent(event, me);
    }

    public EventResponse leave(String email, Long id) {
        User me = findUser(email);
        SchoolEvent event = visibleEvent(id, me);
        participants.findByEventAndUser(event, me).ifPresent(participants::delete);
        return toEvent(event, me);
    }

    @Transactional(readOnly = true)
    public List<ParticipantResponse> participantsOf(String email, Long id) {
        User me = findUser(email);
        SchoolEvent event = ownedEvent(id, me);
        return participants.findByEventOrderByJoinedAtAsc(event).stream()
                .map(p -> new ParticipantResponse(p.getUser().getId(), p.getUser().getName(),
                        photoService.url(p.getUser().getProfilePictureKey(), PhotoRendition.THUMB), p.getJoinedAt()))
                .toList();
    }

    // ─── Account deletion ─────────────────────────────────────────────────────

    /** Their school with its events, and their sign-ups elsewhere; returns photo keys to delete after. */
    public List<String> deleteAllForUser(User user) {
        List<String> keys = new ArrayList<>();
        participants.deleteAll(participants.findByUser(user));
        schools.findByOwner(user).ifPresent(school -> {
            for (SchoolEvent e : events.findBySchoolOrderByStartsAtAsc(school)) {
                participants.deleteAll(participants.findByEventOrderByJoinedAtAsc(e));
                List<SchoolEventPhoto> ps = eventPhotos.findByEventOrderBySortOrderAsc(e);
                ps.forEach(p -> keys.add(p.getStorageKey()));
                eventPhotos.deleteAll(ps);
                events.delete(e);
            }
            if (school.getLogoKey() != null) keys.add(school.getLogoKey());
            schools.delete(school);
        });
        return keys;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void apply(DogSchool s, SchoolRequest r) {
        s.setName(r.name().trim());
        s.setDescription(blankToNull(r.description()));
        s.setAddress(r.address().trim());
        s.setLatitude(r.latitude());
        s.setLongitude(r.longitude());
        s.setPhone(blankToNull(r.phone()));
        s.setWebsite(normalizeWebsite(r.website()));
        s.setOpeningHours(blankToNull(r.openingHours()));
    }

    private void apply(SchoolEvent e, EventRequest r) {
        e.setTitle(r.title().trim());
        e.setDescription(blankToNull(r.description()));
        e.setStartsAt(r.startsAt());
        e.setEndsAt(r.endsAt());
        e.setPlaceName(blankToNull(r.placeName()));
        e.setAddress(blankToNull(r.address()));
        e.setLatitude(r.latitude());
        e.setLongitude(r.longitude());
        e.setPriceRappen(r.priceRappen() == null || r.priceRappen() == 0 ? null : r.priceRappen());
        e.setMaxParticipants(r.maxParticipants());
    }

    private void validate(EventRequest r) {
        if (!r.startsAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The event must be in the future");
        }
        if (r.endsAt() != null && !r.endsAt().isAfter(r.startsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The end must be after the start");
        }
        checkWords(r.title(), r.description(), r.placeName());
    }

    private void checkWords(String... texts) {
        for (String t : texts) {
            if (profanityFilter.containsProfanity(t)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This contains inappropriate language.");
            }
        }
    }

    /** "dogschool.ch" becomes a link that opens. */
    static String normalizeWebsite(String website) {
        String w = blankToNull(website);
        if (w == null) return null;
        return w.startsWith("http://") || w.startsWith("https://") ? w : "https://" + w;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private DogSchool approvedSchoolOf(User me) {
        DogSchool school = schools.findByOwner(me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Only dog schools can add events"));
        if (!DogSchool.APPROVED.equals(school.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your dog school is still being reviewed");
        }
        return school;
    }

    private DogSchool visibleSchool(Long id, User me) {
        DogSchool s = schools.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such school"));
        if (!DogSchool.APPROVED.equals(s.getStatus()) && !s.getOwner().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such school");
        }
        return s;
    }

    private SchoolEvent visibleEvent(Long id, User me) {
        SchoolEvent e = events.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such event"));
        visibleSchool(e.getSchool().getId(), me);
        return e;
    }

    private SchoolEvent ownedEvent(Long id, User me) {
        SchoolEvent e = events.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such event"));
        if (!e.getSchool().getOwner().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your event");
        }
        return e;
    }

    private boolean joined(SchoolEvent e, User me) {
        return participants.findByEventAndUser(e, me).isPresent();
    }

    private boolean withinReach(User me, Double lat, Double lng) {
        if (me.getLatitude() == null || me.getLongitude() == null || lat == null || lng == null) return false;
        double radius = me.getMaxDistanceKm() != null ? me.getMaxDistanceKm() : DEFAULT_RADIUS_KM;
        return GeoUtil.distanceKm(me.getLatitude(), me.getLongitude(), lat, lng) <= radius;
    }

    private double distance(User me, Double lat, Double lng) {
        if (me.getLatitude() == null || me.getLongitude() == null || lat == null || lng == null) return -1;
        return Math.round(GeoUtil.distanceKm(me.getLatitude(), me.getLongitude(), lat, lng) * 10) / 10.0;
    }

    /** An event without its own place is at the school. */
    private static Double lat(SchoolEvent e) {
        return e.getLatitude() != null ? e.getLatitude() : e.getSchool().getLatitude();
    }

    private static Double lng(SchoolEvent e) {
        return e.getLongitude() != null ? e.getLongitude() : e.getSchool().getLongitude();
    }

    private void tellParticipants(SchoolEvent event, String title, String body) {
        for (SchoolEventParticipant p : participants.findByEventOrderByJoinedAtAsc(event)) {
            pushNotificationService.send(p.getUser(), title, body,
                    Map.of("type", "SCHOOL_EVENT_UPDATED", "eventId", event.getId()));
        }
    }

    private SchoolResponse toSchool(DogSchool s, User me, boolean withEvents) {
        boolean mine = s.getOwner().getId().equals(me.getId());
        List<EventResponse> upcoming = withEvents
                ? events.findBySchoolOrderByStartsAtAsc(s).stream()
                    .filter(e -> !e.isCancelled() && e.getStartsAt().isAfter(Instant.now()))
                    .map(e -> toEvent(e, me)).toList()
                : List.of();
        return new SchoolResponse(s.getId(), s.getName(), s.getDescription(), s.getAddress(),
                s.getLatitude(), s.getLongitude(), s.getPhone(), s.getWebsite(), s.getOpeningHours(),
                photoService.url(s.getLogoKey(), PhotoRendition.THUMB),
                mine ? s.getStatus() : DogSchool.APPROVED, mine ? s.getReviewNote() : null,
                distance(me, s.getLatitude(), s.getLongitude()), mine, upcoming);
    }

    private EventResponse toEvent(SchoolEvent e, User me) {
        List<PhotoResponse> ps = eventPhotos.findByEventOrderBySortOrderAsc(e).stream()
                .map(p -> new PhotoResponse(p.getId(),
                        photoService.url(p.getStorageKey(), PhotoRendition.FEED),
                        photoService.url(p.getStorageKey(), PhotoRendition.THUMB)))
                .toList();
        DogSchool s = e.getSchool();
        return new EventResponse(e.getId(), s.getId(), s.getName(), photoService.url(s.getLogoKey(), PhotoRendition.THUMB),
                e.getTitle(), e.getDescription(), e.getStartsAt(), e.getEndsAt(),
                e.getPlaceName() != null ? e.getPlaceName() : s.getName(),
                e.getAddress() != null ? e.getAddress() : s.getAddress(),
                lat(e), lng(e), e.getPriceRappen(), e.getMaxParticipants(),
                (int) participants.countByEvent(e), joined(e, me), s.getOwner().getId().equals(me.getId()),
                e.isCancelled(), distance(me, lat(e), lng(e)), ps);
    }

    private String describe(DogSchool s) {
        return """
                Dog school: %s (id %d)
                Registered by: %s (id %d, %s)
                Address: %s
                Phone: %s
                Website: %s
                Opening hours: %s

                %s

                Review it on https://dogsout.app/admin under Schools.""".formatted(
                s.getName(), s.getId(), s.getOwner().getName(), s.getOwner().getId(), s.getOwner().getEmail(),
                s.getAddress(), s.getPhone(), s.getWebsite(), s.getOpeningHours(),
                s.getDescription() == null ? "" : s.getDescription());
    }

    private void notifyAdmin(String subject, String body) {
        try {
            emailService.sendReportEmail(adminEmail, subject, body);
        } catch (RuntimeException e) {
            log.warn("Could not email the admin about a dog school: {}", e.getMessage());
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
