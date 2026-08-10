package com.dogsout.server.user;

/**
 * @param url      full-size rendition, for carousels and full-bleed cards
 * @param thumbUrl small rendition, for avatars and list rows
 */
public record UserPhotoResponse(Long id, String url, String thumbUrl, Integer sortOrder) {}
