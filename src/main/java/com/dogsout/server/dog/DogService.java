package com.dogsout.server.dog;

import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class DogService {

    private final DogRepository dogRepository;
    private final UserRepository userRepository;

    public DogResponse createDog(String email, DogRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dog name is required");
        }
        User owner = findUser(email);
        Dog dog = new Dog();
        dog.setName(request.name().trim());
        dog.setBreed(request.breed());
        dog.setAge(request.age());
        dog.setBio(request.bio());
        dog.setProfilePicture(request.profilePicture());
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
        if (request.name() != null && !request.name().isBlank()) dog.setName(request.name().trim());
        if (request.breed() != null) dog.setBreed(request.breed());
        if (request.age() != null) dog.setAge(request.age());
        if (request.bio() != null) dog.setBio(request.bio());
        if (request.profilePicture() != null) dog.setProfilePicture(request.profilePicture());
        return toResponse(dogRepository.save(dog));
    }

    public void deleteDog(String email, Long id) {
        Dog dog = findDog(id);
        assertOwner(email, dog);
        dogRepository.delete(dog);
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
        return new DogResponse(
                dog.getId(),
                dog.getName(),
                dog.getBreed(),
                dog.getAge(),
                dog.getBio(),
                dog.getProfilePicture(),
                dog.getOwner().getId(),
                dog.getOwner().getName(),
                dog.getCreatedAt()
        );
    }
}