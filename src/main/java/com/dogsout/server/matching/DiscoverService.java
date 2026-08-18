package com.dogsout.server.matching;

import com.dogsout.server.dog.DogPhotoRepository;
import com.dogsout.server.dog.DogRepository;
import com.dogsout.server.dog.DogResponse;
import com.dogsout.server.dog.DogPhotoResponse;
import com.dogsout.server.moderation.BlockRepository;
import com.dogsout.server.photo.PhotoRendition;
import com.dogsout.server.photo.PhotoService;
import com.dogsout.server.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;


@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DiscoverService {

    private static final double MAX_DISTANCE_KM = 50.0;
    private static final String USER_NOT_FOUND = "User not found";
    /** Tag columns store multiple values joined by "||"; this is the split regex. */
    private static final String TAG_SPLIT_REGEX = "\\|\\|";

    private boolean passesAgeFilter(User u, Integer minAge, Integer maxAge) {
        if (minAge == null && maxAge == null) return true;
        if (u.getDateOfBirth() == null) return true;
        int age = (int) java.time.temporal.ChronoUnit.YEARS.between(u.getDateOfBirth(), java.time.LocalDate.now(java.time.ZoneId.systemDefault()));
        if (minAge != null && age < minAge) return false;
        return maxAge == null || age <= maxAge;
    }

    private final UserRepository userRepository;
    private final UserPhotoRepository userPhotoRepository;
    private final DogRepository dogRepository;
    private final DogPhotoRepository dogPhotoRepository;
    private final MatchRepository matchRepository;
    private final BlockRepository blockRepository;
    private final PhotoService photoService;

    public List<DiscoverProfile> getDiscoverFeed(String email) {
        User me = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, USER_NOT_FOUND));

        // Discover is for dog owners; sitter-only accounts use the seeker pool instead.
        // This rule is symmetric — see the hasDog/dogs filters below, which keep dogless
        // accounts *out* of everyone else's feed too. Enforcing only this half let
        // sitter-only profiles surface as swipe cards.
        if (Boolean.FALSE.equals(me.getHasDog())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Add a dog to use Discover");
        }

        // Exclude users I swiped on AND users already matched with me (where I'm user2)
        java.util.Set<Long> excluded = new java.util.HashSet<>(matchRepository.findSwipedUserIdsByUser1Id(me.getId()));
        excluded.addAll(matchRepository.findMatchedUserIdsByUser2Id(me.getId()));
        // Blocks hide users in both directions
        excluded.addAll(blockRepository.findBlockedIdsByBlockerId(me.getId()));
        excluded.addAll(blockRepository.findBlockerIdsByBlockedId(me.getId()));

        boolean hasLocation = me.getLatitude() != null && me.getLongitude() != null;
        double maxDist = me.getMaxDistanceKm() != null ? me.getMaxDistanceKm() : MAX_DISTANCE_KM;
        // Distance cap always applies when we know the user's location (50 km default)
        boolean distFilterOn = hasLocation;

        return userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .filter(u -> !excluded.contains(u.getId()))
                // No dog, no Discover — in both directions. `null` counts as an owner
                // (legacy rows predate the flag).
                .filter(u -> !Boolean.FALSE.equals(u.getHasDog()))
                .filter(u -> !distFilterOn || (u.getLatitude() != null && u.getLongitude() != null
                        && calculateDistance(me.getLatitude(), me.getLongitude(), u.getLatitude(), u.getLongitude()) <= maxDist))
                .filter(u -> passesAgeFilter(u, me.getMinAge(), me.getMaxAge()))
                .filter(u -> passesDogAgeFilter(u, me.getMinDogAge(), me.getMaxDogAge()))
                .map(u -> toDiscoverProfile(u, me))
                // The flag can disagree with reality (set true, no dog added yet), and a
                // swipe card with no dog on it is broken either way.
                .filter(p -> !p.dogs().isEmpty())
                .sorted((a, b) -> Double.compare(
                        a.distanceKm() < 0 ? Double.MAX_VALUE : a.distanceKm(),
                        b.distanceKm() < 0 ? Double.MAX_VALUE : b.distanceKm()))
                .toList();
    }

    // Both dogsitting pools are opt-in in both directions: you can't browse one side
    // until you've joined the other. Someone with neither toggle sees nothing, which
    // is the point — the lists are for people who've actually opted in.

    /** Owners needing a sitter — the "jobs" side, visible only to sitters. */
    public List<DiscoverProfile> getSeekerPool(String email) {
        User me = requireUser(email);
        if (!Boolean.TRUE.equals(me.getIsSitter())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Turn on \"I'm a dogsitter\" to browse jobs");
        }
        return sitterPool(me, u -> Boolean.TRUE.equals(u.getLookingForSitter()));
    }

    /** Sitters offering to sit — visible only to someone looking for a sitter. */
    public List<DiscoverProfile> getSitterPool(String email) {
        User me = requireUser(email);
        if (!Boolean.TRUE.equals(me.getLookingForSitter())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Turn on \"looking for a dogsitter\" to browse sitters");
        }
        return sitterPool(me, u -> Boolean.TRUE.equals(u.getIsSitter()));
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, USER_NOT_FOUND));
    }

    private List<DiscoverProfile> sitterPool(User me, java.util.function.Predicate<User> role) {
        java.util.Set<Long> excluded = new java.util.HashSet<>(blockRepository.findBlockedIdsByBlockerId(me.getId()));
        excluded.addAll(blockRepository.findBlockerIdsByBlockedId(me.getId()));

        boolean hasLocation = me.getLatitude() != null && me.getLongitude() != null;
        double maxDist = me.getMaxDistanceKm() != null ? me.getMaxDistanceKm() : MAX_DISTANCE_KM;
        boolean distFilterOn = hasLocation;

        return userRepository.findAll().stream()
                .filter(role)
                .filter(u -> !u.getId().equals(me.getId()))
                .filter(u -> !excluded.contains(u.getId()))
                .filter(u -> !distFilterOn || (u.getLatitude() != null && u.getLongitude() != null
                        && calculateDistance(me.getLatitude(), me.getLongitude(), u.getLatitude(), u.getLongitude()) <= maxDist))
                .map(u -> toDiscoverProfile(u, me))
                .sorted((a, b) -> Double.compare(
                        a.distanceKm() < 0 ? Double.MAX_VALUE : a.distanceKm(),
                        b.distanceKm() < 0 ? Double.MAX_VALUE : b.distanceKm()))
                .toList();
    }

    public DiscoverProfile getProfile(String email, Long userId) {
        User me = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, USER_NOT_FOUND));
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, USER_NOT_FOUND));
        // A blocked user's profile behaves as if it no longer exists
        if (blockRepository.existsBlockBetween(me.getId(), target.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, USER_NOT_FOUND);
        }
        return toDiscoverProfile(target, me);
    }

    private DiscoverProfile toDiscoverProfile(User u, User me) {
        double distance = (me.getLatitude() != null && me.getLongitude() != null
                && u.getLatitude() != null && u.getLongitude() != null)
                ? coarseDistanceKm(calculateDistance(
                        me.getLatitude(), me.getLongitude(), u.getLatitude(), u.getLongitude()))
                : -1;

        List<UserPhotoResponse> userPhotos = userPhotoRepository.findByUserOrderBySortOrderAsc(u)
                .stream().map(p -> new UserPhotoResponse(
                        p.getId(),
                        photoService.url(p.getStorageKey(), PhotoRendition.FEED),
                        photoService.url(p.getStorageKey(), PhotoRendition.THUMB),
                        p.getSortOrder())).toList();

        List<DogResponse> dogs = dogRepository.findByOwner(u).stream()
                .map(dog -> {
                    List<DogPhotoResponse> dogPhotos = dogPhotoRepository.findByDogOrderBySortOrderAsc(dog)
                            .stream().map(p -> new DogPhotoResponse(
                                    p.getId(),
                                    photoService.url(p.getStorageKey(), PhotoRendition.FEED),
                                    photoService.url(p.getStorageKey(), PhotoRendition.THUMB),
                                    p.getSortOrder())).toList();
                    return new DogResponse(
                            dog.getId(), dog.getName(), dog.getBreed(), dog.getDateOfBirth(),
                            dog.getBio(), photoService.url(dog.getProfilePictureKey(), PhotoRendition.THUMB),
                            u.getId(), u.getName(), photoService.url(u.getProfilePictureKey(), PhotoRendition.THUMB),
                            dog.getCreatedAt(),
                            dog.getEnergyLevel(), dog.getSocialBehavior(),
                            dog.getLoves() != null ? Arrays.asList(dog.getLoves().split(TAG_SPLIT_REGEX)) : List.of(),
                            dog.getOffLeash(), dog.getKidsComfort(),
                            dog.getTags() != null ? Arrays.asList(dog.getTags().split(TAG_SPLIT_REGEX)) : List.of(),
                            dogPhotos
                    );
                })
                .toList();

        // Expose only the computed age, never the exact birth date
        Integer age = u.getDateOfBirth() == null ? null
                : (int) java.time.temporal.ChronoUnit.YEARS.between(u.getDateOfBirth(), java.time.LocalDate.now(java.time.ZoneId.systemDefault()));

        // "Prefer not to say" is a request to hide the field from other users, not just leave it blank
        String relationshipStatus = "Prefer not to say".equals(u.getRelationshipStatus())
                ? null : u.getRelationshipStatus();

        boolean isSitter = Boolean.TRUE.equals(u.getIsSitter());

        return new DiscoverProfile(
                u.getId(), u.getName(), age, u.getBio(),
                photoService.url(u.getProfilePictureKey(), PhotoRendition.THUMB),
                userPhotos,
                u.getLifestyleTags() != null ? Arrays.asList(u.getLifestyleTags().split(TAG_SPLIT_REGEX)) : List.of(),
                u.getPersonalityTags() != null ? Arrays.asList(u.getPersonalityTags().split(TAG_SPLIT_REGEX)) : List.of(),
                relationshipStatus,
                dogs,
                distance,
                isSitter,
                isSitter && u.getSitterWeekdays() != null ? Arrays.asList(u.getSitterWeekdays().split(TAG_SPLIT_REGEX)) : List.of(),
                isSitter ? u.getSitterExperienceYears() : null,
                isSitter && u.getSitterTags() != null ? Arrays.asList(u.getSitterTags().split(TAG_SPLIT_REGEX)) : List.of(),
                Boolean.TRUE.equals(u.getLookingForSitter())
        );
    }

    private boolean passesDogAgeFilter(User u, Integer minDogAge, Integer maxDogAge) {
        if (minDogAge == null && maxDogAge == null) return true;
        return dogRepository.findByOwner(u).stream().anyMatch(dog -> {
            if (dog.getDateOfBirth() == null) return true;
            int age = (int) java.time.temporal.ChronoUnit.YEARS.between(dog.getDateOfBirth(), java.time.LocalDate.now(java.time.ZoneId.systemDefault()));
            if (minDogAge != null && age < minDogAge) return false;
            return maxDogAge == null || age <= maxDogAge;
        });
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        return com.dogsout.server.GeoUtil.distanceKm(lat1, lon1, lat2, lon2);
    }

    /**
     * Reduces a distance to the whole kilometre the app actually displays.
     *
     * <p>An exact distance is enough to find someone's home. A caller can set their own
     * coordinates to three points — {@code PUT /users/me} accepts any latitude and
     * longitude — read the three exact distances that come back, and solve for the other
     * user's position. That is a handful of ordinary requests, not an attack needing
     * special access, and it is how dating apps have leaked addresses before.
     *
     * <p>Rounding here costs nothing: the client already renders whole kilometres, or
     * "less than 1 km" below that, so no precision that anyone could see is lost. It
     * leaves trilateration resolving to roughly a square kilometre.
     */
    static double coarseDistanceKm(double exactKm) {
        return Math.round(exactKm);
    }
}
