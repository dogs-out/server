package com.dogsout.server.photo;

/**
 * A new framing for a photo already uploaded.
 *
 * <p>All four null means "show the whole picture" — which is how a crop is undone,
 * and the reason the pixels are never touched.
 */
public record SetCropRequest(Double x, Double y, Double width, Double height) {}
