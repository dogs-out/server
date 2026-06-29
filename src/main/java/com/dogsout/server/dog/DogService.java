package com.dogsout.server.dog;

import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
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
public class DogService {

    private final DogRepository dogRepository;
    private final UserRepository userRepository;
    private final DogPhotoRepository dogPhotoRepository;

    public DogResponse createDog(String email, DogRequest request) {
        User owner = findUser(email);
        if (dogRepository.countByOwner(owner) >= 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can add a maximum of 3 dogs.");
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
        applyRequest(dog, request);
        return toResponse(dogRepository.save(dog));
    }

    public void deleteDog(String email, Long id) {
        Dog dog = findDog(id);
        assertOwner(email, dog);
        dogPhotoRepository.deleteAll(dogPhotoRepository.findByDogOrderBySortOrderAsc(dog));
        dogRepository.delete(dog);
    }

    public DogPhotoResponse addPhoto(String email, Long dogId, String imageData) {
        Dog dog = findDog(dogId);
        assertOwner(email, dog);
        long count = dogPhotoRepository.countByDog(dog);
        if (count >= 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 6 photos per dog");
        }
        DogPhoto photo = dogPhotoRepository.save(new DogPhoto(dog, imageData, (int) count));
        if (count == 0) {
            dog.setProfilePicture(imageData);
            dogRepository.save(dog);
        }
        return new DogPhotoResponse(photo.getId(), photo.getImageData(), photo.getSortOrder());
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
        dog.setProfilePicture(remaining.isEmpty() ? null : remaining.get(0).getImageData());
        dogRepository.save(dog);
    }

    private void applyRequest(Dog dog, DogRequest req) {
        if (req.name() != null && !req.name().isBlank()) dog.setName(req.name().trim());
        if (req.breed() != null)        dog.setBreed(req.breed());
        if (req.dateOfBirth() != null)  dog.setDateOfBirth(req.dateOfBirth());
        if (req.bio() != null)          dog.setBio(req.bio());
        if (req.profilePicture() != null) dog.setProfilePicture(req.profilePicture());
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

    private DogResponse toResponse(Dog dog) {
        List<DogPhotoResponse> photos = dogPhotoRepository.findByDogOrderBySortOrderAsc(dog)
                .stream().map(p -> new DogPhotoResponse(p.getId(), p.getImageData(), p.getSortOrder())).toList();
        return new DogResponse(
                dog.getId(),
                dog.getName(),
                dog.getBreed(),
                dog.getDateOfBirth(),
                dog.getBio(),
                dog.getProfilePicture(),
                dog.getOwner().getId(),
                dog.getOwner().getName(),
                dog.getOwner().getProfilePicture(),
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