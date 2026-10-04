package com.dogsout.server.user;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A sitter's week as a grid: which weekday at which time of day, stored as
 * {@code ||}-joined "Day:Slot" pairs, e.g. "Monday:Morning||Wednesday:Afternoon".
 *
 * <p>Replaces the separate weekday and time-of-day lists, which could only say
 * "Mondays and Wednesdays, mornings and afternoons" — never "Monday morning and
 * Wednesday afternoon". Those lists are still written alongside, derived from the
 * grid, so older app versions keep showing something sensible.
 */
public final class Availability {

    public static final List<String> DAYS = List.of(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

    private Availability() {}

    /** Keeps only well-formed pairs, in week order; null when nothing is left. */
    public static String join(List<String> pairs) {
        Set<String> wanted = new LinkedHashSet<>();
        if (pairs != null) for (String p : pairs) if (p != null) wanted.add(p.trim().toLowerCase(Locale.ROOT));
        List<String> kept = new ArrayList<>();
        for (String day : DAYS) {
            for (String slot : TimeSlots.ALL) {
                String pair = day + ":" + slot;
                if (wanted.contains(pair.toLowerCase(Locale.ROOT))) kept.add(pair);
            }
        }
        return kept.isEmpty() ? null : String.join("||", kept);
    }

    public static List<String> split(String stored) {
        if (stored == null || stored.isBlank()) return List.of();
        return Arrays.stream(stored.split("\\|\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** The weekdays that appear anywhere in the grid, for the legacy weekday list. */
    public static List<String> days(String stored) {
        Set<String> days = new LinkedHashSet<>();
        for (String p : split(stored)) days.add(p.substring(0, p.indexOf(':')));
        return DAYS.stream().filter(days::contains).toList();
    }

    /** The times of day that appear anywhere in the grid, for the legacy time-of-day list. */
    public static List<String> slots(String stored) {
        Set<String> slots = new LinkedHashSet<>();
        for (String p : split(stored)) slots.add(p.substring(p.indexOf(':') + 1));
        return TimeSlots.ALL.stream().filter(slots::contains).toList();
    }

    /**
     * Whether a sitter is free at any of the asked combinations. A family's filter is
     * still a list of days and a list of times; every day × time they picked counts,
     * and an empty list means "any". A sitter with no grid falls back to the old
     * separate lists, with the same any-match and "silence means ask me" rules.
     */
    public static boolean matches(User sitter, List<String> askedDays, List<String> askedSlots) {
        Set<String> days = lower(askedDays);
        Set<String> slots = lower(askedSlots);
        if (days.isEmpty() && slots.isEmpty()) return true;

        List<String> grid = split(sitter.getSitterAvailability());
        if (grid.isEmpty()) {
            return weekdaysMatch(sitter.getSitterWeekdays(), days)
                    && TimeSlots.anyMatch(sitter.getSitterTimeSlots(), askedSlots);
        }
        for (String pair : grid) {
            int colon = pair.indexOf(':');
            String day = pair.substring(0, colon).toLowerCase(Locale.ROOT);
            String slot = pair.substring(colon + 1).toLowerCase(Locale.ROOT);
            if ((days.isEmpty() || days.contains(day)) && (slots.isEmpty() || slots.contains(slot))) return true;
        }
        return false;
    }

    private static boolean weekdaysMatch(String stored, Set<String> askedDays) {
        if (askedDays.isEmpty() || stored == null || stored.isBlank()) return true;
        return Arrays.stream(stored.split("\\|\\|"))
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .anyMatch(askedDays::contains);
    }

    private static Set<String> lower(List<String> values) {
        Set<String> out = new LinkedHashSet<>();
        if (values != null) for (String v : values) if (v != null && !v.isBlank()) out.add(v.trim().toLowerCase(Locale.ROOT));
        return out;
    }
}
