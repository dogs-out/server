package com.dogsout.server.notification;

import com.dogsout.server.dog.Dog;
import com.dogsout.server.dog.DogRepository;
import com.dogsout.server.matching.Match;
import com.dogsout.server.matching.MatchRepository;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Tells a dog's matches when it has a birthday.
 *
 * <p>The point is a reason to reopen a conversation that has gone quiet. Every dog
 * supplies one of these a year without anybody having to post anything, which is
 * the rarest property a re-engagement notification can have — it does not need the
 * app to be busy to work.
 *
 * <p>Two kinds of birthday: the dogs', and the people's. Both are told to whoever
 * has matched with that person, because the point is reopening a conversation that
 * has gone quiet. The owner also gets a note about their own dog — it carries no
 * information they lack, but people like being wished a happy birthday for their
 * dog, and it is the one notification nobody minds.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BirthdayService {

    /**
     * Distribution is Switzerland-only, so one wall-clock morning covers everyone.
     * Revisit if the app is ever released more widely: 09:00 in Zürich is the
     * middle of the night in other places, and a birthday push at 03:00 is a
     * reason to turn notifications off.
     */
    private static final String ZONE = "Europe/Zurich";

    private final DogRepository dogRepository;
    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final PushNotificationService pushNotificationService;

    @Scheduled(cron = "0 0 9 * * *", zone = ZONE)
    @Transactional
    public void greetTodaysBirthdays() {
        LocalDate today = LocalDate.now(ZoneId.of(ZONE));

        for (Dog dog : dogRepository.findBirthdaysOn(
                today.getMonthValue(), today.getDayOfMonth(), today.getYear())) {
            greetDog(dog, today);
            dog.setBirthdayGreetedYear(today.getYear());
            dogRepository.save(dog);
        }

        for (User person : userRepository.findBirthdaysOn(
                today.getMonthValue(), today.getDayOfMonth(), today.getYear())) {
            greetPerson(person, today);
            person.setBirthdayGreetedYear(today.getYear());
            userRepository.save(person);
        }
    }

    private void greetDog(Dog dog, LocalDate today) {
        User owner = dog.getOwner();
        if (owner == null) return;

        long years = ChronoUnit.YEARS.between(dog.getDateOfBirth(), today);
        String title = dog.getName() + " turns " + years + " today 🎂";
        String body = "Send " + owner.getName() + " a birthday message for " + dog.getName() + ".";

        int greeted = 0;
        for (Match match : matchRepository.findAllMatchesForUser(owner.getId())) {
            User other = match.getUser1().getId().equals(owner.getId()) ? match.getUser2() : match.getUser1();
            // The chat is the point, so the payload carries what the app needs to
            // open that conversation rather than dropping the user on a list.
            pushNotificationService.send(other, title, body, Map.of(
                    "type", "DOG_BIRTHDAY",
                    "matchId", match.getId(),
                    "otherUserId", owner.getId(),
                    "name", owner.getName()));
            greeted++;
        }
        // And the owner, who gets a different sentence — theirs is a greeting, not
        // a prompt to write to somebody.
        pushNotificationService.send(owner, dog.getName() + " turns " + years + " today 🎂",
                "Happy birthday to " + dog.getName() + "!",
                Map.of("type", "DOG_BIRTHDAY_OWN", "dogId", dog.getId()));

        log.info("Birthday greeting for dog {} ({}) sent to {} match(es) and the owner",
                dog.getId(), dog.getName(), greeted);
    }

    /** A person's own birthday, told to everyone they have matched with. */
    private void greetPerson(User person, LocalDate today) {
        long years = ChronoUnit.YEARS.between(person.getDateOfBirth(), today);
        String title = person.getName() + " turns " + years + " today 🎂";
        String body = "Send " + person.getName() + " a birthday message.";

        int greeted = 0;
        for (Match match : matchRepository.findAllMatchesForUser(person.getId())) {
            User other = match.getUser1().getId().equals(person.getId()) ? match.getUser2() : match.getUser1();
            pushNotificationService.send(other, title, body, Map.of(
                    "type", "USER_BIRTHDAY",
                    "matchId", match.getId(),
                    "otherUserId", person.getId(),
                    "name", person.getName()));
            greeted++;
        }
        log.info("Birthday greeting for user {} sent to {} match(es)", person.getId(), greeted);
    }
}
