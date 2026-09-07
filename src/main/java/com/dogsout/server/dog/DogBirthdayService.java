package com.dogsout.server.dog;

import com.dogsout.server.matching.Match;
import com.dogsout.server.matching.MatchRepository;
import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.user.User;
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
 * <p>The owner is deliberately not told about their own dog's birthday. They know,
 * and a notification that carries no information reads as filler.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DogBirthdayService {

    /**
     * Distribution is Switzerland-only, so one wall-clock morning covers everyone.
     * Revisit if the app is ever released more widely: 09:00 in Zürich is the
     * middle of the night in other places, and a birthday push at 03:00 is a
     * reason to turn notifications off.
     */
    private static final String ZONE = "Europe/Zurich";

    private final DogRepository dogRepository;
    private final MatchRepository matchRepository;
    private final PushNotificationService pushNotificationService;

    @Scheduled(cron = "0 0 9 * * *", zone = ZONE)
    @Transactional
    public void greetTodaysBirthdays() {
        LocalDate today = LocalDate.now(ZoneId.of(ZONE));

        for (Dog dog : dogRepository.findBirthdaysOn(
                today.getMonthValue(), today.getDayOfMonth(), today.getYear())) {
            greet(dog, today);
            dog.setBirthdayGreetedYear(today.getYear());
            dogRepository.save(dog);
        }
    }

    private void greet(Dog dog, LocalDate today) {
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
        log.info("Birthday greeting for dog {} ({}) sent to {} match(es)", dog.getId(), dog.getName(), greeted);
    }
}
