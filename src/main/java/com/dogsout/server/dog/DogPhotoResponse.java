package com.dogsout.server.dog;

import com.dogsout.server.photo.CropRect;

/**
 * @param url      full-size rendition, for carousels and full-bleed cards
 * @param thumbUrl small rendition, for avatars and list rows
 * @param crop     which part of it to show; null means the whole image
 */
public record DogPhotoResponse(Long id, String url, String thumbUrl, Integer sortOrder, CropRect crop) {}
