package com.dogsout.server.dog;

import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DogSexTest {

    @Autowired DogService dogs;
    @Autowired UserRepository users;
    @MockitoBean PushNotificationService push;

    static DogRequest request(String sex) {
        return request(sex, null);
    }

    static DogRequest request(String sex, Boolean neutered) {
        return new DogRequest("Luna", null, null, null, null, null, null, null, sex, neutered, null, null);
    }

    @Test
    void sexIsSavedAndReturned() {
        User u = new User();
        u.setEmail("owner@test.ch");
        u.setName("Owner");
        u.setPassword("x");
        u.setDateOfBirth(LocalDate.of(1990, 1, 1));
        users.save(u);

        DogResponse created = dogs.createDog(u.getEmail(), request("FEMALE"));
        assertThat(created.sex()).isEqualTo("FEMALE");

        // Leaving it out on an update keeps what was there.
        assertThat(dogs.updateDog(u.getEmail(), created.id(), request(null)).sex()).isEqualTo("FEMALE");
    }

    @Test
    void neuteredIsSavedAndKeptWhenLeftOut() {
        User u = new User();
        u.setEmail("neutered@test.ch");
        u.setName("Owner");
        u.setPassword("x");
        u.setDateOfBirth(LocalDate.of(1990, 1, 1));
        users.save(u);

        // Dogs from before the question have no answer, not a "no".
        DogResponse old = dogs.createDog(u.getEmail(), request("MALE"));
        assertThat(old.neutered()).isNull();

        assertThat(dogs.updateDog(u.getEmail(), old.id(), request(null, true)).neutered()).isTrue();
        // An older app version that never sends it must not wipe the answer.
        assertThat(dogs.updateDog(u.getEmail(), old.id(), request(null)).neutered()).isTrue();
        assertThat(dogs.updateDog(u.getEmail(), old.id(), request(null, false)).neutered()).isFalse();
    }

    @Test
    void onlyMaleOrFemaleIsAccepted() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(request("MALE"))).isEmpty();
        assertThat(validator.validate(request(null))).isEmpty();
        assertThat(validator.validate(request("OTHER"))).isNotEmpty();
    }
}
