package com.dogsout.server.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Records that someone used the app: when, on which platform and app version, and
 * on which days. Feeds the admin page's active-user numbers.
 *
 * <p>Called for every authenticated request but writes at most once every few
 * minutes per user (and once more when the day changes), so it costs nothing
 * noticeable. A failure here is logged and swallowed: losing an activity tick must
 * never fail the request it rode in on.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityTracker {

    static final Duration WRITE_EVERY = Duration.ofMinutes(5);
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    /** iOS apps send "DogsOut/25 CFNetwork/… Darwin/…": the number is the build. */
    private static final Pattern IOS_BUILD = Pattern.compile("^[^/]+/(\\d+) CFNetwork");

    private final UserRepository userRepository;
    private final UserActivityDayRepository dayRepository;

    private record Seen(Instant at, LocalDate day) {}
    private final Map<String, Seen> lastWrite = new ConcurrentHashMap<>();

    public void touch(String email, String platformHeader, String versionHeader, String userAgent) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZURICH);
        Seen prev = lastWrite.get(email);
        if (prev != null && today.equals(prev.day()) && prev.at().plus(WRITE_EVERY).isAfter(now)) return;
        lastWrite.put(email, new Seen(now, today));

        String platform = platform(platformHeader, userAgent);
        String version = version(versionHeader, userAgent);
        try {
            userRepository.recordActivity(email, now, platform, version);
            Long userId = userRepository.findIdByEmail(email);
            if (userId != null && !dayRepository.existsByUserIdAndDay(userId, today)) {
                dayRepository.save(new UserActivityDay(userId, today, platform));
            }
        } catch (DataIntegrityViolationException raced) {
            // Two requests recorded the same day at once; the other one won. Fine.
        } catch (RuntimeException e) {
            log.warn("Could not record activity for a user: {}", e.getMessage());
        }
    }

    /**
     * The app says so from 1.3 on. Older builds are recognised by their HTTP client:
     * iOS networking identifies as CFNetwork/Darwin, Android's as okhttp.
     */
    static String platform(String header, String userAgent) {
        if (header != null) {
            String h = header.trim().toLowerCase(Locale.ROOT);
            if (h.equals("ios")) return "IOS";
            if (h.equals("android")) return "ANDROID";
        }
        if (userAgent == null) return null;
        if (userAgent.contains("CFNetwork") || userAgent.contains("Darwin")) return "IOS";
        if (userAgent.toLowerCase(Locale.ROOT).contains("okhttp")) return "ANDROID";
        return null;
    }

    static String version(String header, String userAgent) {
        if (header != null && !header.isBlank()) return header.trim().substring(0, Math.min(header.trim().length(), 32));
        if (userAgent != null) {
            Matcher m = IOS_BUILD.matcher(userAgent);
            if (m.find()) return "build " + m.group(1);
        }
        return null;
    }
}
