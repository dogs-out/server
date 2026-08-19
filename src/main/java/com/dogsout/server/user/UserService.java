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
        if (Boolean.FALSE.equals(user.getHasDog()) && !Boolean.TRUE.equals(user.getIsSitter())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You must either have a dog or be available as a sitter");
        }
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
                !Boolean.FALSE.equals(user.getNotificationsEnabled())
        );
    }
}