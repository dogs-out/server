package com.dogsout.server.user;

/**
 * A friend who came along on a walk, and which of their dogs did.
 *
 * @param dogId   null when no dog was picked — they came along on their own
 * @param dogName likewise
 */
public record StatusCompanion(Long userId, String name, Long dogId, String dogName) {

    /** What the app sends: whose company, and optionally which of their dogs. */
    public record Pick(Long userId, Long dogId) {}
}
