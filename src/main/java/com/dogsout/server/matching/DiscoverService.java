package com.dogsout.server.matching;

import com.dogsout.server.dog.DogPhotoRepository;
import com.dogsout.server.dog.DogRepository;
import com.dogsout.server.dog.DogResponse;
import com.dogsout.server.dog.DogPhotoResponse;
import com.dogsout.server.moderation.BlockRepository;
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

    private boolean passesAgeFilter(User u, Integer minAge, Integer maxAge) {
        if (minAge == null && maxAge == null) return true;
        if (u.getDateOfBirth() == null) return true;
        int age = (int) java.time.temporal.ChronoUnit.YEARS.between(u.getDateOfBirth(), java.time.LocalDate.now());
        if (minAge != null && age < minAge) return false;
        if (maxAge != null && age > maxAge) return false;
        return true;
    }

    private final UserRepository userRepository;
    private final UserPhotoRepository userPhotoRepository;
    private final DogRepository dogRepository;
    private final DogPhotoRepository dogPhotoRepository;
    private final MatchRepository matchRepository;
    private final BlockRepository blockRepository;

    public List<DiscoverProfile> getDiscoverFeed(String email) {
        User me = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

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

    /** Owners advertising that they need a dogsitter — the "jobs" a sitter can pick up. */
    public List<DiscoverProfile> getSeekerPool(String email) {
        return sitterPool(email, u -> Boolean.TRUE.equals(u.getLookingForSitter()));
    }

    /**
     * Users offering to sit — visible only to someone actually looking for a sitter.
     * Sitters publish availability to find work, not to be browsable by everyone, so
     * this pool is gated on the viewer's own lookingForSitter flag.
     */
    public List<DiscoverProfile> getSitterPool(String email) {
        User me = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!Boolean.TRUE.equals(me.getLookingForSitter())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Turn on \"looking for a dogsitter\" to browse sitters");
        }
        return sitterPool(email, u -> Boolean.TRUE.equals(u.getIsSitter()));
    }

    private List<DiscoverProfile> sitterPool(String email, java.util.function.Predicate<User> role) {
        User me = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        // A blocked user's profile behaves as if it no longer exists
        if (blockRepository.existsBlockBetween(me.getId(), target.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return toDiscoverProfile(target, me);
    }

    private DiscoverProfile toDiscoverProfile(User u, User me) {
        double distance = (me.getLatitude() != null && me.getLongitude() != null
                && u.getLatitude() != null && u.getLongitude() != null)
                ? calculateDistance(me.getLatitude(), me.getLongitude(), u.getLatitude(), u.getLongitude())
                : -1;

        List<UserPhotoResponse> userPhotos = userPhotoRepository.findByUserOrderBySortOrderAsc(u)
                .stream().map(p -> new UserPhotoResponse(p.getId(), p.getImageData(), p.getSortOrder())).toList();

        List<DogResponse> dogs = dogRepository.findByOwner(u).stream()
                .map(dog -> {
                    List<DogPhotoResponse> dogPhotos = dogPhotoRepository.findByDogOrderBySortOrderAsc(dog)
                            .stream().map(p -> new DogPhotoResponse(p.getId(), p.getImageData(), p.getSortOrder())).toList();
                    return new DogResponse(
                            dog.getId(), dog.getName(), dog.getBreed(), dog.getDateOfBirth(),
                            dog.getBio(), dog.getProfilePicture(),
                            u.getId(), u.getName(), u.getProfilePicture(),
                            dog.getCreatedAt(),
                            dog.getEnergyLevel(), dog.getSocialBehavior(),
                            dog.getLoves() != null ? Arrays.asList(dog.getLoves().split("\\|\\|")) : List.of(),
                            dog.getOffLeash(), dog.getKidsComfort(),
                            dog.getTags() != null ? Arrays.asList(dog.getTags().split("\\|\\|")) : List.of(),
                            dogPhotos
                    );
                })
                .toList();

        // Expose only the computed age, never the exact birth date
        Integer age = u.getDateOfBirth() == null ? null
                : (int) java.time.temporal.ChronoUnit.YEARS.between(u.getDateOfBirth(), java.time.LocalDate.now());

        // "Prefer not to say" is a request to hide the field from other users, not just leave it blank
        String relationshipStatus = "Prefer not to say".equals(u.getRelationshipStatus())
                ? null : u.getRelationshipStatus();

        boolean isSitter = Boolean.TRUE.equals(u.getIsSitter());

        return new DiscoverProfile(
                u.getId(), u.getName(), age, u.getBio(), u.getProfilePicture(),
                userPhotos,
                u.getLifestyleTags() != null ? Arrays.asList(u.getLifestyleTags().split("\\|\\|")) : List.of(),
                u.getPersonalityTags() != null ? Arrays.asList(u.getPersonalityTags().split("\\|\\|")) : List.of(),
                relationshipStatus,
                dogs,
                distance,
                isSitter,
                isSitter && u.getSitterWeekdays() != null ? Arrays.asList(u.getSitterWeekdays().split("\\|\\|")) : List.of(),
                isSitter ? u.getSitterExperienceYears() : null,
                isSitter && u.getSitterTags() != null ? Arrays.asList(u.getSitterTags().split("\\|\\|")) : List.of(),
                Boolean.TRUE.equals(u.getLookingForSitter())
        );
    }

    private boolean passesDogAgeFilter(User u, Integer minDogAge, Integer maxDogAge) {
        if (minDogAge == null && maxDogAge == null) return true;
        return dogRepository.findByOwner(u).stream().anyMatch(dog -> {
            if (dog.getDateOfBirth() == null) return true;
            int age = (int) java.time.temporal.ChronoUnit.YEARS.between(dog.getDateOfBirth(), java.time.LocalDate.now());
            if (minDogAge != null && age < minDogAge) return false;
            if (maxDogAge != null && age > maxDogAge) return false;
            return true;
        });
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        return com.dogsout.server.GeoUtil.distanceKm(lat1, lon1, lat2, lon2);
    }
}
