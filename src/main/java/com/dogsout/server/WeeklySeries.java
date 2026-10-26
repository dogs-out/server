package com.dogsout.server;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Something that repeats every week on some weekdays: a sitting, a dog-school course.
 *
 * <p>Stored as the first occurrence's start, the last one's end, the weekdays and
 * the length of one occurrence. Everything that only asks "is it over?" or "when
 * does it end?" then keeps working on startsAt/endsAt unchanged. Times are Swiss
 * wall-clock times, so 13:00 stays 13:00 across the change to winter time.
 */
public final class WeeklySeries {

    public static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    private WeeklySeries() {}

    public static Set<DayOfWeek> weekdays(List<String> names) {
        try {
            return names.stream().map(n -> DayOfWeek.valueOf(n.trim().toUpperCase()))
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown weekday");
        }
    }

    /** "TUESDAY,FRIDAY", in weekday order. */
    public static String encode(Set<DayOfWeek> days) {
        return days.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    /**
     * Checks a series' first occurrence and last day.
     *
     * @param maxDays how far the last day may lie after the first
     */
    public static void validate(Instant firstStart, Instant firstEnd, Set<DayOfWeek> days, LocalDate until, int maxDays) {
        if (days.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick at least one weekday");
        if (Duration.between(firstStart, firstEnd).compareTo(Duration.ofHours(24)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One occurrence lasts at most a day");
        }
        ZonedDateTime first = firstStart.atZone(ZONE);
        if (!days.contains(first.getDayOfWeek())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The first date has to fall on one of the weekdays");
        }
        if (until.isBefore(first.toLocalDate()) || until.isAfter(first.toLocalDate().plusDays(maxDays))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The series ends too far away");
        }
    }

    /** The end of the last occurrence on or before {@code until}. */
    public static Instant lastEnd(Instant firstStart, Instant firstEnd, Set<DayOfWeek> days, LocalDate until) {
        ZonedDateTime first = firstStart.atZone(ZONE);
        Duration length = Duration.between(firstStart, firstEnd);
        LocalDate last = until;
        while (!days.contains(last.getDayOfWeek())) last = last.minusDays(1);
        return last.atTime(first.toLocalTime()).atZone(ZONE).plus(length).toInstant();
    }

    /**
     * Up to {@code limit} occurrence starts that have not ended by {@code now},
     * leaving out the days in {@code skip} (classes called off).
     */
    public static List<Instant> upcoming(Instant startsAt, Instant endsAt, String weekdays, int minutes,
                                         Instant now, Set<LocalDate> skip, int limit) {
        Set<DayOfWeek> days = weekdays(List.of(weekdays.split(",")));
        ZonedDateTime first = startsAt.atZone(ZONE);
        LocalDate day = first.toLocalDate();
        LocalDate today = now.atZone(ZONE).toLocalDate();
        if (day.isBefore(today)) day = today.minusDays(1);
        List<Instant> out = new java.util.ArrayList<>();
        for (int i = 0; i < 800 && out.size() < limit; i++, day = day.plusDays(1)) {
            if (!days.contains(day.getDayOfWeek()) || skip.contains(day)) continue;
            ZonedDateTime start = day.atTime(first.toLocalTime()).atZone(ZONE);
            if (start.toInstant().isBefore(startsAt)) continue;
            if (endsAt != null && start.toInstant().isAfter(endsAt)) break;
            if (start.plusMinutes(minutes).toInstant().isAfter(now)) out.add(start.toInstant());
        }
        return out;
    }

    /**
     * The start of the next occurrence that has not ended by {@code now}, or null
     * once the series is over. For a single event this is just its start.
     */
    public static Instant nextStart(Instant startsAt, Instant endsAt, String weekdays, Integer minutes, Instant now) {
        if (weekdays == null || weekdays.isBlank() || minutes == null) {
            return endsAt != null && endsAt.isBefore(now) ? null : startsAt;
        }
        Set<DayOfWeek> days = weekdays(List.of(weekdays.split(",")));
        ZonedDateTime first = startsAt.atZone(ZONE);
        LocalDate day = first.toLocalDate();
        LocalDate today = now.atZone(ZONE).toLocalDate();
        if (day.isBefore(today)) day = today.minusDays(1);
        for (int i = 0; i < 400; i++, day = day.plusDays(1)) {
            if (!days.contains(day.getDayOfWeek())) continue;
            ZonedDateTime start = day.atTime(first.toLocalTime()).atZone(ZONE);
            if (start.toInstant().isBefore(startsAt)) continue;
            if (endsAt != null && start.toInstant().isAfter(endsAt)) return null;
            if (start.plusMinutes(minutes).toInstant().isAfter(now)) return start.toInstant();
        }
        return null;
    }
}
