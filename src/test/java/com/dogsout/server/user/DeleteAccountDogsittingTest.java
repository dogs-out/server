package com.dogsout.server.user;

import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.sitter.SitterCancellation;
import com.dogsout.server.sitter.SitterCancellationRepository;
import com.dogsout.server.sitter.SitterReview;
import com.dogsout.server.sitter.SitterReviewRepository;
import com.dogsout.server.sitter.SittingRequest;
import com.dogsout.server.sitter.SittingRequestRepository;
import com.dogsout.server.sitter.SittingRequestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting an account with dogsitting history used to fail outright: jobs, reviews
 * and cancellation strikes all reference the user row, and nothing removed them
 * first. Runs against a real (H2) database so the foreign keys are enforced.
 */
@SpringBootTest
@Transactional
class DeleteAccountDogsittingTest {

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired SittingRequestRepository requests;
    @Autowired SitterReviewRepository reviews;
    @Autowired SitterCancellationRepository cancellations;
    @MockitoBean PushNotificationService push;

    User user(String name) {
        User u = new User();
        u.setEmail(name.toLowerCase() + "@test");
        u.setName(name);
        u.setPassword("x");
        u.setDateOfBirth(LocalDate.of(1990, 1, 1));
        return userRepository.save(u);
    }

    SittingRequest job(User owner, User sitter, Instant startsAt) {
        SittingRequest r = new SittingRequest();
        r.setOwner(owner);
        r.setSitter(sitter);
        r.setStartsAt(startsAt);
        r.setEndsAt(startsAt.plus(3, ChronoUnit.HOURS));
        r.setDogIds("1");
        r.setStatus(sitter == null ? SittingRequestStatus.OPEN : SittingRequestStatus.CLOSED);
        if (sitter != null) r.setAcceptedAt(Instant.now());
        return requests.save(r);
    }

    void review(SittingRequest job, User rater, User sitter) {
        SitterReview r = new SitterReview();
        r.setRequest(job);
        r.setRater(rater);
        r.setSitter(sitter);
        r.setStars(5);
        reviews.save(r);
    }

    @Test
    void ownerWithJobsAndReviewsCanDeleteTheirAccount() {
        User owner = user("Owner");
        User sitter = user("Sitter");
        Instant past = Instant.now().minus(10, ChronoUnit.DAYS);
        SittingRequest done = job(owner, sitter, past);
        review(done, owner, sitter);

        userService.deleteAccount(owner.getEmail());

        assertThat(userRepository.findById(owner.getId())).isEmpty();
        assertThat(requests.findById(done.getId())).isEmpty();
        assertThat(reviews.count()).isZero();
        assertThat(userRepository.findById(sitter.getId())).isPresent();
    }

    @Test
    void sitterDeletionReopensFutureJobsAndDetachesPastOnes() {
        User owner = user("Owner");
        User sitter = user("Sitter");
        SittingRequest done = job(owner, sitter, Instant.now().minus(10, ChronoUnit.DAYS));
        SittingRequest upcoming = job(owner, sitter, Instant.now().plus(2, ChronoUnit.DAYS));
        review(done, owner, sitter);
        SitterCancellation strike = new SitterCancellation();
        strike.setSitter(sitter);
        strike.setRequestId(upcoming.getId());
        strike.setLate(true);
        strike.setSittingStartsAt(upcoming.getStartsAt());
        cancellations.save(strike);

        userService.deleteAccount(sitter.getEmail());

        assertThat(userRepository.findById(sitter.getId())).isEmpty();
        assertThat(reviews.count()).isZero();
        assertThat(cancellations.count()).isZero();
        SittingRequest reopened = requests.findById(upcoming.getId()).orElseThrow();
        assertThat(reopened.getSitter()).isNull();
        assertThat(reopened.getStatus()).isEqualTo(SittingRequestStatus.OPEN);
        SittingRequest finished = requests.findById(done.getId()).orElseThrow();
        assertThat(finished.getSitter()).isNull();
    }
}
