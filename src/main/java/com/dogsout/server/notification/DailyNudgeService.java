package com.dogsout.server.notification;

import com.dogsout.server.bones.BoneService;
import com.dogsout.server.matching.DiscoverService;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * One reason a day to open the app: new dog owners nearby, bones that are back, or
 * a weekend playdate to plan.
 *
 * <p>At most one push a day, at 18:00 when people are out with the dog anyway; only
 * to someone who has not opened the app that day (they need no reminder), and only
 * to people active in the last two weeks — nagging a dormant account is how an app
 * gets its notifications turned off for good. Can be switched off in Settings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyNudgeService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Zurich");
    private static final Duration ACTIVE_WITHIN = Duration.ofDays(14);
    /** Who counts as new: joined within the last week. */
    private static final Duration NEW_WITHIN = Duration.ofDays(7);
    private static final LocalDateTime EVERYONE = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final UserRepository userRepository;
    private final DiscoverService discoverService;
    private final BoneService boneService;
    private final PushNotificationService pushNotificationService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DailyDigest digest(User user) {
        boolean owner = !Boolean.FALSE.equals(user.getHasDog());
        return new DailyDigest(
                owner ? discoverService.countNewInDeck(user, LocalDateTime.now(clock.withZone(ZONE)).minus(NEW_WITHIN)) : 0,
                owner ? discoverService.countNewInDeck(user, EVERYONE) : 0,
                boneService.status(user),
                owner);
    }

    @Scheduled(cron = "0 0 18 * * *", zone = "Europe/Zurich")
    @Transactional
    public void sendDailyNudges() {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        java.time.Instant startOfToday = today.atStartOfDay(ZONE).toInstant();
        int sent = 0;
        for (User user : userRepository.findDailyNudgeCandidates(clock.instant().minus(ACTIVE_WITHIN), today)) {
            // Opened the app today already: they know.
            if (user.getLastActiveAt() != null && !user.getLastActiveAt().isBefore(startOfToday)) continue;
            // Bones and the daily card arrive with 1.4; an older app would be told about
            // things it does not have. Unknown version counts as older here.
            if (!knowsBones(user.getLastAppVersion())) continue;
            DailyDigest digest = digest(user);
            Nudge nudge = choose(digest, today.getDayOfWeek());
            if (nudge == null) continue;
            String[] text = nudge.text(language(user), digest.bones().dailyAllowance());
            pushNotificationService.send(user, text[0], text[1], Map.of("type", "DAILY_NUDGE", "screen", nudge.screen()));
            user.setLastDailyNudgeDay(today);
            userRepository.save(user);
            sent++;
        }
        log.info("Daily nudge sent to {} users", sent);
    }

    static boolean knowsBones(String appVersion) {
        return appVersion != null && !com.dogsout.server.bones.AppVersion.isOlderThan(appVersion, 1, 4);
    }

    /** The most useful thing to say today, or nothing if there is nothing worth a push. */
    static Nudge choose(DailyDigest d, DayOfWeek day) {
        if (d.newNearby() > 0) return new Nudge.NewNearby(d.newNearby());
        // "Your bones are back" to an empty deck would send them to nothing.
        if (d.deckSize() > 0 && d.bones().remaining() > 0) return new Nudge.BonesBack();
        if (d.canHost() && (day == DayOfWeek.THURSDAY || day == DayOfWeek.FRIDAY)) return new Nudge.PlanPlaydate();
        return null;
    }

    private static String language(User user) {
        return user.getAppLanguage() != null ? user.getAppLanguage() : "de";
    }

    sealed interface Nudge {
        /** The app screen the push opens. */
        String screen();

        /** Title and body in the given language. */
        String[] text(String lang, int allowance);

        record NewNearby(int count) implements Nudge {
            public String screen() { return "Discover"; }

            public String[] text(String lang, int allowance) {
                boolean one = count == 1;
                return switch (lang) {
                    case "en" -> new String[]{
                            one ? "A new dog owner near you 🐾" : count + " new dog owners near you 🐾",
                            "Your bones are ready. Say hi with a treat."};
                    case "fr" -> new String[]{
                            one ? "Un nouveau propriétaire de chien près de chez toi 🐾"
                                : count + " nouveaux propriétaires de chiens près de chez toi 🐾",
                            "Tes os sont prêts. Dis bonjour avec une friandise."};
                    case "it" -> new String[]{
                            one ? "Un nuovo proprietario di cani vicino a te 🐾"
                                : count + " nuovi proprietari di cani vicino a te 🐾",
                            "I tuoi ossi sono pronti. Saluta con un premio."};
                    default -> new String[]{
                            one ? "Ein neuer Hundebesitzer in deiner Nähe 🐾"
                                : count + " neue Hundebesitzer in deiner Nähe 🐾",
                            "Deine Knochen sind bereit. Sag Hallo mit einem Leckerli."};
                };
            }
        }

        record BonesBack() implements Nudge {
            public String screen() { return "Discover"; }

            public String[] text(String lang, int allowance) {
                return switch (lang) {
                    case "en" -> new String[]{"Your " + allowance + " bones are back 🦴", "Who will you treat today?"};
                    case "fr" -> new String[]{"Tes " + allowance + " os sont de retour 🦴", "À qui vas-tu donner une friandise aujourd'hui ?"};
                    case "it" -> new String[]{"I tuoi " + allowance + " ossi sono tornati 🦴", "A chi darai un premio oggi?"};
                    default -> new String[]{"Deine " + allowance + " Knochen sind wieder da 🦴", "Wem gibst du heute ein Leckerli?"};
                };
            }
        }

        record PlanPlaydate() implements Nudge {
            public String screen() { return "CreatePlaydate"; }

            public String[] text(String lang, int allowance) {
                return switch (lang) {
                    case "en" -> new String[]{"Plan a playdate for the weekend? 🌳", "Pick a park and see who joins."};
                    case "fr" -> new String[]{"Un playdate ce week-end ? 🌳", "Choisis un parc et vois qui te rejoint."};
                    case "it" -> new String[]{"Un playdate per il weekend? 🌳", "Scegli un parco e guarda chi si unisce."};
                    default -> new String[]{"Playdate fürs Wochenende planen? 🌳", "Wähl einen Park und schau, wer mitkommt."};
                };
            }
        }
    }
}
