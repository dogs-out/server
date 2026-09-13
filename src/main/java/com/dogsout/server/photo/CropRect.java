package com.dogsout.server.photo;

/**
 * Which part of a stored photo is shown, as fractions of the whole image.
 *
 * <p>Fractions rather than pixels so the rectangle survives the renditions: the
 * same numbers describe the same framing whether they are applied to the 1080px
 * feed image or a 256px thumbnail.
 *
 * <p>The point of keeping this beside the photo rather than baking it into the
 * pixels is that cropping in stays reversible. The stored image is always the
 * whole picture; a crop that shaved the edges off could never be widened again,
 * which is exactly what was asked for.
 *
 * @param x      left edge, 0 at the far left
 * @param y      top edge, 0 at the top
 * @param width  fraction of the image's width
 * @param height fraction of the image's height
 */
public record CropRect(double x, double y, double width, double height) {

    /** The whole image — what a photo nobody has framed is showing. */
    public static final CropRect FULL = new CropRect(0, 0, 1, 1);

    /**
     * Builds a rectangle from a request, or null when it describes the whole
     * image. Values are clamped rather than rejected: a rectangle off the edge
     * is a rounding error on a device, not something worth failing an upload for.
     */
    public static CropRect of(Double x, Double y, Double width, Double height) {
        if (x == null || y == null || width == null || height == null) return null;
        // A rectangle we cannot read is not a framing. Clamping a non-finite
        // width to the minimum would turn a garbled request into a 1% pinhole
        // crop, which is a far worse answer than showing the whole picture.
        if (!finite(x) || !finite(y) || !finite(width) || !finite(height)) return null;

        double w = clamp(width, 0.01, 1);
        double h = clamp(height, 0.01, 1);
        double left = clamp(x, 0, 1 - w);
        double top = clamp(y, 0, 1 - h);

        CropRect rect = new CropRect(left, top, w, h);
        return rect.isWhole() ? null : rect;
    }

    /** Null and "the whole image" mean the same thing, so only one is ever stored. */
    public boolean isWhole() {
        return x <= 0 && y <= 0 && width >= 1 && height >= 1;
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }
}
