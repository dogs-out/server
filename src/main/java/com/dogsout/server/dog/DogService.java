package com.dogsout.server.dog;

import com.dogsout.server.ProfanityFilter;
import com.dogsout.server.photo.PhotoRendition;
import com.dogsout.server.photo.PhotoService;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class DogService {

    private final DogRepository dogRepository;
    private final UserRepository userRepository;
    private final DogPhotoRepository dogPhotoRepository;
    private final ProfanityFilter profanityFilter;
    private final PhotoService photoService;

    public DogResponse createDog(String email, DogRequest request) {
        User owner = findUser(email);
        if (dogRepository.countByOwner(owner) >= 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can add a maximum of 3 dogs.");
        }
        checkProfanity(request.name(), request.bio());
        if (Boolean.FALSE.equals(owner.getHasDog())) {
            owner.setHasDog(true);
            userRepository.save(owner);
        }
        Dog dog = new Dog();
        applyRequest(dog, request);
        dog.setOwner(owner);
        return toResponse(dogRepository.save(dog));
    }

    @Transactional(readOnly = true)
    public List<DogResponse> getMyDogs(String email) {
        User owner = findUser(email);
        return dogRepository.findByOwner(owner).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DogResponse getDog(Long id) {
        return toResponse(findDog(id));
    }

    public DogResponse updateDog(String email, Long id, DogRequest request) {
        Dog dog = findDog(id);
        assertOwner(email, dog);
        checkProfanity(request.name(), request.bio());
        applyRequest(dog, request);
        return toResponse(dogRepository.save(dog));
    }

    public void deleteDog(String email, Long id) {
        Dog dog = findDog(id);
        assertOwner(email, dog);
        List<DogPhoto> photos = dogPhotoRepository.findByDogOrderBySortOrderAsc(dog);
        List<String> keys = new ArrayList<>(photos.stream().map(DogPhoto::getStorageKey).toList());
        dogPhotoRepository.deleteAll(photos);
        dogRepository.delete(dog);
        keys.forEach(photoService::delete);
    }

    public DogPhotoResponse addPhoto(String email, Long dogId, MultipartFile file) {
        Dog dog = findDog(dogId);
        assertOwner(email, dog);
        long count = dogPhotoRepository.countByDog(dog);
        if (count >= 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 6 photos per dog");
        }
        String key = photoService.store(PhotoService.OWNER_DOG, file);
        DogPhoto photo = dogPhotoRepository.save(new DogPhoto(dog, key, (int) count));
        if (count == 0) {
            dog.setProfilePictureKey(key);
            dogRepository.save(dog);
        }
        return toPhotoResponse(photo);
    }

    public void deletePhoto(String email, Long dogId, Long photoId) {
        Dog dog = findDog(dogId);
        assertOwner(email, dog);
        DogPhoto photo = dogPhotoRepository.findById(photoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
        if (!photo.getDog().getId().equals(dogId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your photo");
        }
        dogPhotoRepository.delete(photo);
        List<DogPhoto> remaining = dogPhotoRepository.findByDogOrderBySortOrderAsc(dog);
        dog.setProfilePictureKey(remaining.isEmpty() ? null : remaining.get(0).getStorageKey());
        dogRepository.save(dog);
        photoService.delete(photo.getStorageKey());
    }

    public void reorderPhotos(String email, Long dogId, List<Long> photoIds) {
        Dog dog = findDog(dogId);
        assertOwner(email, dog);
        List<DogPhoto> photos = dogPhotoRepository.findByDogOrderBySortOrderAsc(dog);
        List<Long> ownedIds = photos.stream().map(DogPhoto::getId).toList();
        if (photoIds.size() != ownedIds.size() || !ownedIds.containsAll(photoIds)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "photoIds must contain exactly this dog's photo ids");
        }
        for (DogPhoto photo : photos) {
            photo.setSortOrder(photoIds.indexOf(photo.getId()));
            if (photo.getSortOrder() == 0) {
                dog.setProfilePictureKey(photo.getStorageKey());
            }
        }
        dogPhotoRepository.saveAll(photos);
        dogRepository.save(dog);
    }

    private void checkProfanity(String name, String bio) {
        if (profanityFilter.containsProfanity(name))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your dog's name contains inappropriate language.");
        if (profanityFilter.containsProfanity(bio))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your dog's bio contains inappropriate language.");
    }

    private void applyRequest(Dog dog, DogRequest req) {
        if (req.name() != null && !req.name().isBlank()) dog.setName(req.name().trim());
        if (req.breed() != null)        dog.setBreed(req.breed());
        if (req.dateOfBirth() != null)  dog.setDateOfBirth(req.dateOfBirth());
        if (req.bio() != null)          dog.setBio(req.bio());
        if (req.energyLevel() != null)  dog.setEnergyLevel(req.energyLevel());
        if (req.socialBehavior() != null) dog.setSocialBehavior(req.socialBehavior());
        if (req.offLeash() != null)     dog.setOffLeash(req.offLeash());
        if (req.kidsComfort() != null)  dog.setKidsComfort(req.kidsComfort());
        if (req.loves() != null)        dog.setLoves(req.loves().isEmpty() ? null : String.join("||", req.loves()));
        if (req.tags() != null)         dog.setTags(req.tags().isEmpty() ? null : String.join("||", req.tags()));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Dog findDog(Long id) {
        return dogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dog not found"));
    }

    private void assertOwner(String email, Dog dog) {
        if (!dog.getOwner().getEmail().equals(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your dog");
        }
    }

    private DogPhotoResponse toPhotoResponse(DogPhoto photo) {
        return new DogPhotoResponse(
                photo.getId(),
                photoService.url(photo.getStorageKey(), PhotoRendition.FEED),
                photoService.url(photo.getStorageKey(), PhotoRendition.THUMB),
                photo.getSortOrder());
    }

    private DogResponse toResponse(Dog dog) {
        List<DogPhotoResponse> photos = dogPhotoRepository.findByDogOrderBySortOrderAsc(dog)
                .stream().map(this::toPhotoResponse).toList();
        return new DogResponse(
                dog.getId(),
                dog.getName(),
                dog.getBreed(),
                dog.getDateOfBirth(),
                dog.getBio(),
                photoService.url(dog.getProfilePictureKey(), PhotoRendition.THUMB),
                dog.getOwner().getId(),
                dog.getOwner().getName(),
                photoService.url(dog.getOwner().getProfilePictureKey(), PhotoRendition.THUMB),
                dog.getCreatedAt(),
                dog.getEnergyLevel(),
                dog.getSocialBehavior(),
                dog.getLoves() != null ? Arrays.asList(dog.getLoves().split("\\|\\|")) : List.of(),
                dog.getOffLeash(),
                dog.getKidsComfort(),
                dog.getTags() != null ? Arrays.asList(dog.getTags().split("\\|\\|")) : List.of(),
                photos
        );
    }
}