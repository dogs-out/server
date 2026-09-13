package com.dogsout.server.user;

import com.dogsout.server.photo.CropRect;

/**
 * @param url      full-size rendition, for carousels and full-bleed cards
 * @param thumbUrl small rendition, for avatars and list rows
 * @param crop     which part of it to show; null means the whole image
 */
public record UserPhotoResponse(Long id, String url, String thumbUrl, Integer sortOrder, CropRect crop) {}
