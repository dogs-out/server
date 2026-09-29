package com.dogsout.server.user;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Times of day a sitter can take a dog: Morning (08–12), Afternoon (12–16) and
 * Evening (16–20). Stored `||`-joined on the user, like the weekdays.
 *
 * <p>Plain strings rather than an enum column, deliberately: Hibernate writes a
 * CHECK constraint for an enum and leaves it stale under ddl-auto=update, which
 * has broken production here before.
 */
public final class TimeSlots {

    public static final List<String> ALL = List.of("Morning", "Afternoon", "Evening");

    private TimeSlots() {}

    /** Keeps only known slots, in the canonical order; null when nothing is left. */
    public static String join(List<String> slots) {
        Set<String> wanted = normalise(slots);
        List<String> kept = ALL.stream().filter(s -> wanted.contains(s.toLowerCase(Locale.ROOT))).toList();
        return kept.isEmpty() ? null : String.join("||", kept);
    }

    public static List<String> split(String stored) {
        if (stored == null || stored.isBlank()) return List.of();
        return Arrays.stream(stored.split("\\|\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * Whether a sitter is free at any of the times asked for — the same rules as the
     * weekdays. Any rather than all, since "mornings or evenings" is someone with two
     * gaps to cover; nothing asked for means everyone; and a sitter who named no
     * times stays in the list, because silence is "ask me", not "never".
     */
    public static boolean anyMatch(String stored, List<String> wanted) {
        Set<String> asked = normalise(wanted);
        if (asked.isEmpty()) return true;
        List<String> offered = split(stored);
        if (offered.isEmpty()) return true;
        return offered.stream().anyMatch(s -> asked.contains(s.toLowerCase(Locale.ROOT)));
    }

    private static Set<String> normalise(List<String> slots) {
        Set<String> out = new LinkedHashSet<>();
        if (slots == null) return out;
        for (String s : slots) {
            if (s != null && !s.isBlank()) out.add(s.trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
