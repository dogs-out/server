package com.dogsout.server.user;

/**
 * What someone is up to right now, shown to people nearby.
 *
 * <p>Only {@link #WALKING} and {@link #ON_VACATION} may carry a point: the first
 * is an invitation to come and say hello, the second explains an absence. Being
 * at home or busy is about availability, not place, and broadcasting a home
 * address is precisely what this app should not do.
 */
public enum WalkStatus {
    WALKING,
    AT_HOME,
    ON_VACATION,
    BUSY;

    public boolean mayShareLocation() {
        return this == WALKING || this == ON_VACATION;
    }
}
