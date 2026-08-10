package com.dogsout.server.dog;

/**
 * @param url      full-size rendition, for carousels and full-bleed cards
 * @param thumbUrl small rendition, for avatars and list rows
 */
public record DogPhotoResponse(Long id, String url, String thumbUrl, Integer sortOrder) {}
