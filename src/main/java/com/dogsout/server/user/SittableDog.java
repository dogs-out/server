package com.dogsout.server.user;

/**
 * A dog you could say you are looking after: one belonging to somebody you have
 * matched with.
 *
 * <p>Carries the owner's name because a sitter may well know two dogs called Luna,
 * and picking the wrong one puts the wrong name in front of everyone they match with.
 */
public record SittableDog(
        Long dogId,
        String name,
        String breed,
        String profilePicture,
        Long ownerId,
        String ownerName
) {}
