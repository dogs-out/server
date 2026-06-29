package com.dogsout.server.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserPhotoRepository userPhotoRepository;

    @Transactional(readOnly = true)
    public UserResponse getMe(String email) {
        return toResponse(findUser(email));
    }

    public UserResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = findUser(email);
        if (request.name() != null)             user.setName(request.name());
        if (request.bio() != null)              user.setBio(request.bio());
        if (request.dateOfBirth() != null)      user.setDateOfBirth(request.dateOfBirth());
        if (request.latitude() != null)         user.setLatitude(request.latitude());
        if (request.longitude() != null)        user.setLongitude(request.longitude());
        if (request.profilePicture() != null)   user.setProfilePicture(request.profilePicture());
        if (request.lifestyleTags() != null)    user.setLifestyleTags(request.lifestyleTags().isEmpty() ? null : String.join("||", request.lifestyleTags()));
        if (request.personalityTags() != null)  user.setPersonalityTags(request.personalityTags().isEmpty() ? null : String.join("||", request.personalityTags()));
        if (request.relationshipStatus() != null) user.setRelationshipStatus(request.relationshipStatus());
        userRepository.save(user);
        return toResponse(user);
    }

    public UserPhotoResponse addPhoto(String email, String imageData) {
        User user = findUser(email);
        long count = userPhotoRepository.countByUser(user);
        if (count >= 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 3 photos per profile");
        }
        UserPhoto photo = userPhotoRepository.save(new UserPhoto(user, imageData, (int) count));
        if (count == 0) {
            user.setProfilePicture(imageData);
            userRepository.save(user);
        }
        return new UserPhotoResponse(photo.getId(), photo.getImageData(), photo.getSortOrder());
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
        user.setProfilePicture(remaining.isEmpty() ? null : remaining.get(0).getImageData());
        userRepository.save(user);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private UserResponse toResponse(User user) {
        List<UserPhotoResponse> photos = userPhotoRepository.findByUserOrderBySortOrderAsc(user)
                .stream().map(p -> new UserPhotoResponse(p.getId(), p.getImageData(), p.getSortOrder())).toList();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getDateOfBirth(),
                user.getBio(),
                user.getProfilePicture(),
                user.getLatitude(),
                user.getLongitude(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getAuthProvider() != null ? user.getAuthProvider().name() : null,
                user.getLifestyleTags() != null ? Arrays.asList(user.getLifestyleTags().split("\\|\\|")) : List.of(),
                user.getPersonalityTags() != null ? Arrays.asList(user.getPersonalityTags().split("\\|\\|")) : List.of(),
                user.getRelationshipStatus(),
                user.getCreatedAt(),
                photos
        );
    }
}