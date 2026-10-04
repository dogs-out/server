package com.dogsout.server.user;

/**
 * A friend who came along on a walk, and which of their dogs did.
 *
 * @param dogName null when no dog was picked — they came along on their own
 */
public record StatusCompanion(Long userId, String name, String dogName) {

    /** What the app sends: whose company, and optionally which of their dogs. */
    public record Pick(Long userId, Long dogId) {}
}
