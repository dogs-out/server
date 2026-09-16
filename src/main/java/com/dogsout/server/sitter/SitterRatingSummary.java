package com.dogsout.server.sitter;

/**
 * The one-line version of a sitter's reviews, for a profile header.
 *
 * @param average rounded to one decimal; 0 when there are none yet
 */
public record SitterRatingSummary(double average, long count) {}
