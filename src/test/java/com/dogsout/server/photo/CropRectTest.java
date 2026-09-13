package com.dogsout.server.photo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rectangle exists so that cropping in can be undone. Everything here is
 * about it never quietly becoming something the user did not choose.
 */
class CropRectTest {

    @Test
    void aRectangleIsKeptAsGiven() {
        CropRect rect = CropRect.of(0.1, 0.2, 0.5, 0.6);
        assertThat(rect).isEqualTo(new CropRect(0.1, 0.2, 0.5, 0.6));
    }

    @Test
    void theWholeImageIsStoredAsNothingAtAll() {
        // Null and "all of it" are the same state; keeping one spelling of it
        // means a photo either has a framing or does not.
        assertThat(CropRect.of(0.0, 0.0, 1.0, 1.0)).isNull();
        assertThat(new CropRect(0, 0, 1, 1).isWhole()).isTrue();
    }

    @Test
    void anAbsentRectangleIsNotACrop() {
        assertThat(CropRect.of(null, null, null, null)).isNull();
        assertThat(CropRect.of(0.1, null, 0.5, 0.5)).isNull();
    }

    @Test
    void aRectangleOffTheEdgeIsPulledBackInside() {
        // Rounding on a device, not something to fail an upload over.
        CropRect rect = CropRect.of(0.9, 0.9, 0.5, 0.5);
        assertThat(rect.x() + rect.width()).isLessThanOrEqualTo(1.0);
        assertThat(rect.y() + rect.height()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void negativeAndOversizeValuesAreClamped() {
        CropRect rect = CropRect.of(-0.5, -0.5, 2.0, 2.0);
        assertThat(rect).isNull();   // clamps to the whole image, which is no crop

        CropRect small = CropRect.of(0.0, 0.0, 0.5, 2.0);
        assertThat(small.height()).isEqualTo(1.0);
        assertThat(small.width()).isEqualTo(0.5);
    }

    @Test
    void aRectangleThatCannotBeReadIsNoRectangle() {
        // Clamping a non-finite width to the minimum would turn a garbled request
        // into a 1% pinhole crop — a far worse answer than the whole picture.
        assertThat(CropRect.of(Double.NaN, Double.NaN, 0.5, 0.5)).isNull();
        assertThat(CropRect.of(0.0, 0.0, Double.POSITIVE_INFINITY, Double.NaN)).isNull();
        assertThat(CropRect.of(0.0, 0.0, 0.5, Double.NEGATIVE_INFINITY)).isNull();
    }

    @Test
    void aRectangleNeverCollapsesToNothing() {
        CropRect rect = CropRect.of(0.5, 0.5, 0.0, 0.0);
        assertThat(rect.width()).isGreaterThan(0);
        assertThat(rect.height()).isGreaterThan(0);
    }
}
