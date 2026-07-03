package com.dogsout.server.matching;

import com.dogsout.server.dog.DogPhotoRepository;
import com.dogsout.server.dog.DogRepository;
import com.dogsout.server.dog.DogResponse;
import com.dogsout.server.dog.DogPhotoResponse;
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

    public List<DiscoverProfile> getDiscoverFeed(String email) {
        User me = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<Long> alreadySwiped = matchRepository.findSwipedUserIdsByUser1Id(me.getId());
        boolean hasLocation = me.getLatitude() != null && me.getLongitude() != null;
        double maxDist = me.getMaxDistanceKm() != null ? me.getMaxDistanceKm() : MAX_DISTANCE_KM;
        boolean distFilterOn = hasLocation && me.getMaxDistanceKm() != null;

        return userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .filter(u -> !alreadySwiped.contains(u.getId()))
                .filter(u -> !distFilterOn || (u.getLatitude() != null && u.getLongitude() != null
                        && calculateDistance(me.getLatitude(), me.getLongitude(), u.getLatitude(), u.getLongitude()) <= maxDist))
                .filter(u -> passesAgeFilter(u, me.getMinAge(), me.getMaxAge()))
                .filter(u -> passesDogAgeFilter(u, me.getMinDogAge(), me.getMaxDogAge()))
                .map(u -> toDiscoverProfile(u, me))
                .sorted((a, b) -> Double.compare(
                        a.distanceKm() < 0 ? Double.MAX_VALUE : a.distanceKm(),
                        b.distanceKm() < 0 ? Double.MAX_VALUE : b.distanceKm()))
                .toList();
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

        return new DiscoverProfile(
                u.getId(), u.getName(), u.getDateOfBirth(), u.getBio(), u.getProfilePicture(),
                userPhotos,
                u.getLifestyleTags() != null ? Arrays.asList(u.getLifestyleTags().split("\\|\\|")) : List.of(),
                u.getPersonalityTags() != null ? Arrays.asList(u.getPersonalityTags().split("\\|\\|")) : List.of(),
                u.getRelationshipStatus(),
                dogs,
                distance
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

    private static final double EARTH_RADIUS = 6371.0;
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double la1 = Math.toRadians(lat1);
        double la2 = Math.toRadians(lat2);
        double lo1 = Math.toRadians(lon1);
        double lo2 = Math.toRadians(lon2);
        double a = (Math.sin((la2-la1)/2)*Math.sin((la2-la1)/2) + Math.cos(la1) * Math.cos(la2) * Math.sin((lo2-lo1)/2)*Math.sin((lo2-lo1)/2));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - Math.min(1.0, a)));
        return EARTH_RADIUS * c;

    }
}
