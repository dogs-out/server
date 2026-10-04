package com.dogsout.server.dog;

import com.dogsout.server.ProfanityFilter;
import com.dogsout.server.photo.PhotoService;
import com.dogsout.server.sos.LostDogAlertRepository;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Every dog keeps at least one photo; the last one cannot be deleted on its own. */
@ExtendWith(MockitoExtension.class)
class DogLastPhotoTest {

    @Mock DogRepository dogRepository;
    @Mock UserRepository userRepository;
    @Mock DogPhotoRepository dogPhotoRepository;
    @Mock ProfanityFilter profanityFilter;
    @Mock PhotoService photoService;
    @Mock LostDogAlertRepository lostDogAlertRepository;

    DogService service;
    Dog dog;
    DogPhoto photo;

    @BeforeEach
    void setUp() {
        service = new DogService(dogRepository, userRepository, dogPhotoRepository,
                profanityFilter, photoService, lostDogAlertRepository);
        User owner = new User();
        owner.setEmail("owner@test");
        dog = new Dog();
        dog.setId(1L);
        dog.setOwner(owner);
        photo = new DogPhoto(dog, "photos/dog/a", 0);
        photo.setId(10L);
        when(dogRepository.findById(1L)).thenReturn(Optional.of(dog));
        when(dogPhotoRepository.findById(10L)).thenReturn(Optional.of(photo));
    }

    @Test
    void lastPhotoCannotBeDeleted() {
        when(dogPhotoRepository.countByDog(dog)).thenReturn(1L);

        assertThatThrownBy(() -> service.deletePhoto("owner@test", 1L, 10L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(dogPhotoRepository, never()).delete(any());
    }

    @Test
    void onePhotoOfSeveralCanBeDeleted() {
        when(dogPhotoRepository.countByDog(dog)).thenReturn(2L);
        when(dogPhotoRepository.findByDogOrderBySortOrderAsc(dog)).thenReturn(List.of());

        service.deletePhoto("owner@test", 1L, 10L);

        verify(dogPhotoRepository).delete(photo);
    }
}
