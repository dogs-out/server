package com.dogsout.server.user;

/**
 * What someone is up to right now, shown to the people they have matched with.
 *
 * <p>The four that may carry a point are the ones where a place is the point of
 * saying anything at all: out walking, at a park, sitting someone's dog, or away.
 * Being at home or busy is about availability, not place, and broadcasting a home
 * address is precisely what this app should not do.
 *
 * <p>Each status also carries how long it may stand. A walk is an afternoon at
 * most; a holiday is measured in weeks. Sharing one range across all of them
 * meant a slider where nearly every stop was wrong for the status in hand.
 */
public enum WalkStatus {
    /** Out with your own dog. */
    WALKING(1, 5),
    /** At a particular park, which is a place you can be joined at. */
    AT_THE_PARK(1, 5),
    /** Looking after someone else's dog — for sitters, this is their version of a walk. */
    SITTING(1, 504),
    /** Away. Explains an absence rather than inviting anyone anywhere. */
    ON_VACATION(72, 504),
    AT_HOME(1, 168),
    BUSY(1, 168);

    private final int minHours;
    private final int maxHours;

    WalkStatus(int minHours, int maxHours) {
        this.minHours = minHours;
        this.maxHours = maxHours;
    }

    public int minHours() {
        return minHours;
    }

    public int maxHours() {
        return maxHours;
    }

    /** Clamps rather than rejects: a duration slightly out of range is not worth failing a request over. */
    public int clampHours(Integer hours) {
        if (hours == null) return minHours;
        return Math.min(maxHours, Math.max(minHours, hours));
    }

    public boolean mayShareLocation() {
        return this == WALKING || this == AT_THE_PARK || this == SITTING || this == ON_VACATION;
    }

    /**
     * Whether this counts as being out and about, as opposed to explaining where
     * you are not. Drives who shows up under "Who's outside" and who may invite.
     */
    public boolean isOutAndAbout() {
        return this == WALKING || this == AT_THE_PARK || this == SITTING;
    }

    /** Sitting is the one status that names someone else's dog. */
    public boolean needsSatDog() {
        return this == SITTING;
    }
}
