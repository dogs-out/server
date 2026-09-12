package com.dogsout.server.user;

import com.dogsout.server.ProfanityFilter;
import com.dogsout.server.chat.MessageRepository;
import com.dogsout.server.dog.Dog;
import com.dogsout.server.dog.DogPhotoRepository;
import com.dogsout.server.dog.DogRepository;
import com.dogsout.server.matching.MatchRepository;
import com.dogsout.server.moderation.BlockRepository;
import com.dogsout.server.photo.PhotoRendition;
import com.dogsout.server.photo.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import com.dogsout.server.user.AuthProvider;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    /** Tag columns store multiple values joined by "||"; this is the split regex. */
    private static final String TAG_SPLIT_REGEX = "\\|\\|";

    private final UserRepository userRepository;
    private final UserPhotoRepository userPhotoRepository;
    private final DogRepository dogRepository;
    private final DogPhotoRepository dogPhotoRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProfanityFilter profanityFilter;
    private final MessageRepository messageRepository;
    private final MatchRepository matchRepository;
    private final BlockRepository blockRepository;
    private final com.dogsout.server.playdate.PlaydateService playdateService;
    private final PhotoService photoService;
    private final com.dogsout.server.notification.PushNotificationService pushNotificationService;

    @Transactional(readOnly = true)
    public UserResponse getMe(String email) {
        return toResponse(findUser(email));
    }

    public UserResponse updateProfile(String email, UpdateProfileRequest request) {
        if (profanityFilter.containsProfanity(request.name()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your name contains inappropriate language.");
        if (profanityFilter.containsProfanity(request.bio()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your bio contains inappropriate language.");
        if (request.dateOfBirth() != null) requireAdult(request.dateOfBirth());
        User user = findUser(email);
        // Every account has to carry an age. Validating the value only helps if one is
        // present, and accounts can otherwise reach the app without ever supplying one:
        // registration does not ask for it, and neither does a Google or Apple sign-in.
        if (user.getDateOfBirth() == null && request.dateOfBirth() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please add your date of birth to your profile before continuing");
        }
        if (request.name() != null)             user.setName(request.name());
        if (request.bio() != null)              user.setBio(request.bio());
        if (request.dateOfBirth() != null)      user.setDateOfBirth(request.dateOfBirth());
        if (request.latitude() != null)         user.setLatitude(request.latitude());
        if (request.longitude() != null)        user.setLongitude(request.longitude());
        if (request.lifestyleTags() != null)    user.setLifestyleTags(request.lifestyleTags().isEmpty() ? null : String.join("||", request.lifestyleTags()));
        if (request.personalityTags() != null)  user.setPersonalityTags(request.personalityTags().isEmpty() ? null : String.join("||", request.personalityTags()));
        if (request.relationshipStatus() != null) user.setRelationshipStatus(request.relationshipStatus());
        if (request.hasDog() != null)             user.setHasDog(request.hasDog());
        if (request.isSitter() != null)           user.setIsSitter(request.isSitter());
        if (request.lookingForSitter() != null)   user.setLookingForSitter(request.lookingForSitter());
        if (request.sitterWeekdays() != null)     user.setSitterWeekdays(request.sitterWeekdays().isEmpty() ? null : String.join("||", request.sitterWeekdays()));
        if (request.sitterExperienceYears() != null) user.setSitterExperienceYears(request.sitterExperienceYears());
        if (request.sitterTags() != null)         user.setSitterTags(request.sitterTags().isEmpty() ? null : String.join("||", request.sitterTags()));
        // Deliberately no "you must have a dog or be a sitter" rule any more. Losing
        // your last dog is a real thing that happens, and the app's answer to it is
        // the add-or-adopt screen, not a profile that refuses to save.
        if (request.maxDistanceKm() != null)      user.setMaxDistanceKm(request.maxDistanceKm() <= 0 ? null : request.maxDistanceKm());
        if (request.minAge() != null)             user.setMinAge(request.minAge() <= 0 ? null : request.minAge());
        if (request.maxAge() != null)             user.setMaxAge(request.maxAge() <= 0 ? null : request.maxAge());
        if (request.minDogAge() != null)          user.setMinDogAge(request.minDogAge() < 0 ? null : request.minDogAge());
        if (request.maxDogAge() != null)          user.setMaxDogAge(request.maxDogAge() <= 0 ? null : request.maxDogAge());
        userRepository.save(user);
        return toResponse(user);
    }

    public void changePassword(String email, ChangePasswordRequest request) {
        User user = findUser(email);
        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password change is not available for social login accounts");
        }
        if (user.getPassword() == null || !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(java.time.Instant.now());
        userRepository.save(user);
    }

    public UserPhotoResponse addPhoto(String email, MultipartFile file) {
        User user = findUser(email);
        long count = userPhotoRepository.countByUser(user);
        if (count >= 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 3 photos per profile");
        }
        String key = photoService.store(PhotoService.OWNER_USER, file);
        UserPhoto photo = userPhotoRepository.save(new UserPhoto(user, key, (int) count));
        if (count == 0) {
            user.setProfilePictureKey(key);
            userRepository.save(user);
        }
        return toPhotoResponse(photo);
    }

    public void deletePhoto(String email, Long photoId) {
        User user = findUser(email);
        UserPhoto photo = userPhotoRepository.findById(photoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
        if (!photo.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your photo");
        }
        userPhotoRepository.delete(photo);
        List<UserPhoto> remaining = userPhotoRepository.findByUserOrderBySortOrderAsc(user);
        user.setProfilePictureKey(remaining.isEmpty() ? null : remaining.get(0).getStorageKey());
        userRepository.save(user);
        // Only once the row is gone, so a storage failure can't orphan the record.
        photoService.delete(photo.getStorageKey());
    }

    public void reorderPhotos(String email, List<Long> photoIds) {
        User user = findUser(email);
        List<UserPhoto> photos = userPhotoRepository.findByUserOrderBySortOrderAsc(user);
        List<Long> ownedIds = photos.stream().map(UserPhoto::getId).toList();
        if (photoIds.size() != ownedIds.size() || !ownedIds.containsAll(photoIds)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "photoIds must contain exactly your photo ids");
        }
        for (UserPhoto photo : photos) {
            photo.setSortOrder(photoIds.indexOf(photo.getId()));
            if (photo.getSortOrder() == 0) {
                user.setProfilePictureKey(photo.getStorageKey());
            }
        }
        userPhotoRepository.saveAll(photos);
        userRepository.save(user);
    }

    public void setPushToken(String email, String token) {
        User user = findUser(email);
        user.setExpoPushToken(token == null || token.isBlank() ? null : token);
        userRepository.save(user);
    }

    public void setNotificationsEnabled(String email, boolean enabled) {
        User user = findUser(email);
        user.setNotificationsEnabled(enabled);
        userRepository.save(user);
    }

    public void deleteAccount(String email) {
        User user = findUser(email);
        playdateService.deleteAllForUser(user);
        // Messages reference matches, so they must go first
        messageRepository.deleteBySenderOrReceiver(user, user);
        matchRepository.deleteByUser1OrUser2(user, user);
        blockRepository.deleteByBlockerOrBlocked(user, user);
        List<Dog> dogs = dogRepository.findByOwner(user);
        // Collect the keys before the rows go, then drop the bytes after. Deleting the
        // account has to take the photos with it — they sit in a publicly fetchable
        // bucket, so a surviving object is a deleted user's face still on the internet.
        List<String> keys = new java.util.ArrayList<>();
        dogs.forEach(dog -> {
            List<com.dogsout.server.dog.DogPhoto> dogPhotos = dogPhotoRepository.findByDogOrderBySortOrderAsc(dog);
            dogPhotos.forEach(p -> keys.add(p.getStorageKey()));
            dogPhotoRepository.deleteAll(dogPhotos);
        });
        dogRepository.deleteAll(dogs);
        List<UserPhoto> userPhotos = userPhotoRepository.findByUserOrderBySortOrderAsc(user);
        userPhotos.forEach(p -> keys.add(p.getStorageKey()));
        userPhotoRepository.deleteAll(userPhotos);
        userRepository.delete(user);
        keys.forEach(photoService::delete);
    }

    /** Dogs Out is an adults-only service; its terms and its store age ratings all say 18+. */
    private static final int MIN_AGE_YEARS = 18;

    /**
     * Rejects a date of birth belonging to a minor.
     *
     * <p>The app already refuses these at the signup screen, but that check lives in the
     * client and anything talking to the API directly can simply skip it. Since the
     * published child-safety standards state that under-18s are not permitted, the rule
     * has to hold at the only place it cannot be bypassed.
     *
     * <p>Someone born exactly {@value #MIN_AGE_YEARS} years ago today is old enough — the
     * comparison is deliberately strict so the birthday itself counts.
     */
    static void requireAdult(java.time.LocalDate dateOfBirth) {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.systemDefault());
        if (dateOfBirth.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date of birth cannot be in the future");
        }
        if (dateOfBirth.isAfter(today.minusYears(MIN_AGE_YEARS))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You must be at least " + MIN_AGE_YEARS + " years old to use Dogs Out");
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private UserPhotoResponse toPhotoResponse(UserPhoto photo) {
        return new UserPhotoResponse(
                photo.getId(),
                photoService.url(photo.getStorageKey(), PhotoRendition.FEED),
                photoService.url(photo.getStorageKey(), PhotoRendition.THUMB),
                photo.getSortOrder());
    }

    private UserResponse toResponse(User user) {
        List<UserPhotoResponse> photos = userPhotoRepository.findByUserOrderBySortOrderAsc(user)
                .stream().map(this::toPhotoResponse).toList();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getDateOfBirth(),
                user.getBio(),
                // profilePicture is an avatar everywhere it is consumed, so it carries
                // the thumb. Full-size images come from photos[].url.
                photoService.url(user.getProfilePictureKey(), PhotoRendition.THUMB),
                user.getLatitude(),
                user.getLongitude(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getAuthProvider() != null ? user.getAuthProvider().name() : null,
                user.getLifestyleTags() != null ? Arrays.asList(user.getLifestyleTags().split(TAG_SPLIT_REGEX)) : List.of(),
                user.getPersonalityTags() != null ? Arrays.asList(user.getPersonalityTags().split(TAG_SPLIT_REGEX)) : List.of(),
                user.getRelationshipStatus(),
                !Boolean.FALSE.equals(user.getHasDog()),
                Boolean.TRUE.equals(user.getIsSitter()),
                Boolean.TRUE.equals(user.getLookingForSitter()),
                user.getSitterWeekdays() != null ? Arrays.asList(user.getSitterWeekdays().split(TAG_SPLIT_REGEX)) : List.of(),
                user.getSitterExperienceYears(),
                user.getSitterTags() != null ? Arrays.asList(user.getSitterTags().split(TAG_SPLIT_REGEX)) : List.of(),
                user.getCreatedAt(),
                photos,
                user.getMaxDistanceKm(),
                user.getMinAge(),
                user.getMaxAge(),
                user.getMinDogAge(),
                user.getMaxDogAge(),
                !Boolean.FALSE.equals(user.getNotificationsEnabled()),
                user.getTermsAcceptedAt() != null,
                WalkStatus.orDefault(user.activeWalkStatus()).name(),
                user.activeWalkStatus() == null ? null : user.getWalkStatusExpiresAt(),
                user.activeWalkStatus() == null ? null : user.getWalkStatusLatitude(),
                user.activeWalkStatus() == null ? null : user.getWalkStatusLongitude(),
                user.activeWalkStatus() == null ? null : user.getWalkStatusPlaceName(),
                user.activeWalkStatus() == null || user.getWalkStatusDog() == null
                        ? null : user.getWalkStatusDog().getId(),
                user.activeWalkStatus() == null
                        ? null : photoService.url(user.getWalkStatusPhotoKey(), PhotoRendition.FEED),
                celebratingToday(user),
                isSameDayOfYear(user.getDateOfBirth(), LocalDate.now(ZoneId.systemDefault())),
                dogBirthdaysToday(user)
        );
    }

    /**
     * Sets or clears the current status.
     *
     * <p>A point is dropped for any status that may not carry one, rather than
     * rejected: the app should not be able to publish where someone lives by
     * sending the wrong pair of fields, and silently narrowing is safer than
     * trusting the caller to have got it right.
     */
    public UserResponse updateStatus(String email, UpdateStatusRequest request) {
        User user = findUser(email);

        // A photo belongs to the status it was taken for, so changing the status
        // drops it rather than leaving yesterday's park attached to tonight's.
        String previousPhoto = user.getWalkStatusPhotoKey();

        if (request.status() == null) {
            clearStatus(user);
        } else {
            WalkStatus status = request.status();
            user.setWalkStatus(status);
            // At home is the resting state and stands until something else is
            // chosen; everything else is a claim about right now and must expire.
            user.setWalkStatusExpiresAt(status.expires()
                    ? Instant.now().plus(Duration.ofHours(status.clampHours(request.hours())))
                    : null);

            boolean sharesPoint = status.mayShareLocation()
                    && request.latitude() != null && request.longitude() != null;
            user.setWalkStatusLatitude(sharesPoint ? request.latitude() : null);
            user.setWalkStatusLongitude(sharesPoint ? request.longitude() : null);
            user.setWalkStatusPlaceName(sharesPoint ? trimToNull(request.placeName()) : null);
            user.setWalkStatusDog(status.needsSatDog() ? requireSittableDog(user, request.dogId()) : null);
            // Keeping the photo is opt-in, so a plain status update clears it.
            user.setWalkStatusPhotoKey(Boolean.TRUE.equals(request.keepPhoto()) ? previousPhoto : null);
        }
        userRepository.save(user);
        if (previousPhoto != null && !previousPhoto.equals(user.getWalkStatusPhotoKey())) {
            photoService.delete(previousPhoto);
        }
        return toResponse(user);
    }

    private static void clearStatus(User user) {
        user.setWalkStatus(null);
        user.setWalkStatusExpiresAt(null);
        user.setWalkStatusLatitude(null);
        user.setWalkStatusLongitude(null);
        user.setWalkStatusPlaceName(null);
        user.setWalkStatusDog(null);
        user.setWalkStatusPhotoKey(null);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Attaches a photo to the current status.
     *
     * <p>Separate from setting the status because it is a file upload, and because
     * it is optional — most statuses will never have one.
     */
    public UserResponse setStatusPhoto(String email, MultipartFile file) {
        User user = findUser(email);
        if (user.activeWalkStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Set a status before adding a photo to it");
        }
        String previous = user.getWalkStatusPhotoKey();
        user.setWalkStatusPhotoKey(photoService.store(PhotoService.OWNER_USER, file));
        userRepository.save(user);
        if (previous != null) photoService.delete(previous);
        return toResponse(user);
    }

    public UserResponse removeStatusPhoto(String email) {
        User user = findUser(email);
        String previous = user.getWalkStatusPhotoKey();
        user.setWalkStatusPhotoKey(null);
        userRepository.save(user);
        if (previous != null) photoService.delete(previous);
        return toResponse(user);
    }

    /**
     * The dogs someone may say they are looking after: those belonging to people
     * they have matched with.
     *
     * <p>Matching is this app's record that two people have agreed to be in
     * contact, and it is already how a sitter reaches an owner. Anything wider
     * would let a stranger attach their status to a dog — and a name in a
     * notification is exactly the sort of thing that reads as legitimate.
     */
    public List<SittableDog> sittableDogs(String email) {
        User me = findUser(email);
        return matchRepository.findAllMatchesForUser(me.getId()).stream()
                .map(match -> match.getUser1().getId().equals(me.getId()) ? match.getUser2() : match.getUser1())
                .flatMap(owner -> dogRepository.findByOwner(owner).stream()
                        .map(dog -> new SittableDog(
                                dog.getId(),
                                dog.getName(),
                                dog.getBreed(),
                                photoService.url(dog.getProfilePictureKey(), PhotoRendition.THUMB),
                                owner.getId(),
                                owner.getName())))
                .toList();
    }

    private Dog requireSittableDog(User me, Long dogId) {
        if (dogId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick the dog you are looking after");
        }
        Dog dog = dogRepository.findById(dogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dog not found"));
        boolean matched = matchRepository.findAllMatchesForUser(me.getId()).stream()
                .anyMatch(match -> match.getUser1().getId().equals(dog.getOwner().getId())
                        || match.getUser2().getId().equals(dog.getOwner().getId()));
        if (!matched) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only sit for people you have matched with");
        }
        return dog;
    }


    /**
     * Matches of this user who are out walking and have shared where.
     *
     * <p>Matches only, and that is the whole safety model: a live-ish location is
     * not something to hand to everyone within a radius, and a match is the app's
     * existing record that both people agreed to be in contact.
     */
    @Transactional(readOnly = true)
    public List<FriendStatus> friendStatuses(String email) {
        User me = findUser(email);

        return matchRepository.findAllMatchesForUser(me.getId()).stream()
                .map(match -> match.getUser1().getId().equals(me.getId()) ? match.getUser2() : match.getUser1())
                // No filter at all: every match appears with whatever they are up
                // to. A point is optional even for the ones who are out, so a row
                // without one simply does not open a map.
                .map(other -> new FriendStatus(
                        other.getId(),
                        other.getName(),
                        photoService.url(other.getProfilePictureKey(), PhotoRendition.THUMB),
                        dogsNamedBy(other),
                        WalkStatus.orDefault(other.activeWalkStatus()).name(),
                        other.getWalkStatusLatitude(),
                        other.getWalkStatusLongitude(),
                        other.getWalkStatusPlaceName(),
                        photoService.url(other.getWalkStatusPhotoKey(), PhotoRendition.FEED),
                        other.getWalkStatusExpiresAt(),
                        distanceTo(me, other)))
                // Whoever is out comes first — that is the part you can act on —
                // then nearest first within each group, with the ones who shared no
                // point after them rather than at the top, where a distance of -1
                // would otherwise put them.
                .sorted(java.util.Comparator
                        .comparing((FriendStatus f) -> !WalkStatus.valueOf(f.status()).isOutAndAbout())
                        .thenComparingDouble(f -> f.distanceKm() < 0 ? Double.MAX_VALUE : f.distanceKm())
                        .thenComparing(FriendStatus::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Tells this user's matches that they are out, once they ask for it.
     *
     * <p>Deliberately a separate action rather than a side effect of setting the
     * status: someone who walks twice a day would otherwise push everyone they
     * have matched with twice a day, and that is how people learn to turn
     * notifications off for good.
     */
    public void inviteMatchesToWalk(String email, List<Long> userIds) {
        User me = findUser(email);
        WalkStatus status = me.activeWalkStatus();
        if (status == null || !status.isOutAndAbout()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You are not out right now");
        }

        String title = inviteTitle(me, status);
        // An empty or absent selection means everyone. Anything else is filtered
        // against the matches rather than trusted: this endpoint's whole safety
        // property is that it can only ever reach people who already agreed.
        boolean everyone = userIds == null || userIds.isEmpty();

        for (var match : matchRepository.findAllMatchesForUser(me.getId())) {
            User other = match.getUser1().getId().equals(me.getId()) ? match.getUser2() : match.getUser1();
            if (!everyone && !userIds.contains(other.getId())) continue;

            pushNotificationService.send(other, title, "Want to join?", java.util.Map.of(
                    "type", "WALK_INVITE",
                    "matchId", match.getId(),
                    "otherUserId", me.getId(),
                    "name", me.getName()));
        }
    }

    /**
     * Whose dogs the status is about: your own when you are walking them, and the
     * one you are looking after when you are sitting.
     */
    /** Says what is actually happening, since "walking" is now only one of three ways to be out. */
    private String inviteTitle(User me, WalkStatus status) {
        List<String> dogs = dogsNamedBy(me);
        String what = dogs.isEmpty() ? "their dog" : String.join(" and ", dogs);
        String where = me.getWalkStatusPlaceName() == null ? "" : " at " + me.getWalkStatusPlaceName();

        return switch (status) {
            case AT_THE_PARK -> me.getName() + " is at the park with " + what + where + " 🐾";
            case SITTING -> me.getName() + " is out with " + what + where + " 🐾";
            default -> me.getName() + " is walking " + what + where + " 🐾";
        };
    }

    private List<String> dogsNamedBy(User user) {
        if (user.activeWalkStatus() == WalkStatus.SITTING) {
            return user.getWalkStatusDog() == null
                    ? List.of() : List.of(user.getWalkStatusDog().getName());
        }
        return dogRepository.findByOwner(user).stream().map(Dog::getName).toList();
    }

    /**
     * Their own birthday, or one of their dogs'. Compared on month and day only,
     * the same rule DiscoverService applies to everyone else's.
     */
    private boolean celebratingToday(User user) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        return isSameDayOfYear(user.getDateOfBirth(), today) || !dogBirthdaysToday(user).isEmpty();
    }

    /** Which of their dogs has a birthday today — the greeting needs the name, not a count. */
    private List<String> dogBirthdaysToday(User user) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        return dogRepository.findByOwner(user).stream()
                .filter(dog -> isSameDayOfYear(dog.getDateOfBirth(), today))
                .map(Dog::getName)
                .toList();
    }

    private static boolean isSameDayOfYear(LocalDate date, LocalDate today) {
        return date != null
                && date.getMonthValue() == today.getMonthValue()
                && date.getDayOfMonth() == today.getDayOfMonth();
    }

    /** -1 where either side has no point to measure from — the row then hides the distance. */
    private double distanceTo(User me, User other) {
        if (me.getLatitude() == null || me.getLongitude() == null) return -1;
        if (other.getWalkStatusLatitude() == null || other.getWalkStatusLongitude() == null) return -1;
        return Math.round(com.dogsout.server.GeoUtil.distanceKm(
                me.getLatitude(), me.getLongitude(), other.getWalkStatusLatitude(), other.getWalkStatusLongitude()));
    }

    /**
     * Records that this account has accepted the terms.
     *
     * <p>Idempotent on purpose: a retry after a dropped response should not look
     * like a second acceptance, and the first time is the one that matters.
     */
    public void acceptTerms(String email) {
        User user = findUser(email);
        if (user.getTermsAcceptedAt() == null) {
            user.setTermsAcceptedAt(java.time.Instant.now());
            userRepository.save(user);
        }
    }
}