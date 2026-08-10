package com.dogsout.server.photo;

import java.io.ByteArrayOutputStream;

/**
 * Strips metadata segments out of a JPEG without touching its compressed image data.
 *
 * <p>Needed because {@link ImageProcessor} sometimes keeps the uploaded bytes rather
 * than re-encoding them (when re-encoding would only make the file bigger). Those
 * bytes come straight off a phone, so they carry EXIF — including GPS coordinates.
 * Re-encoding drops metadata as a side effect; keeping the original does not, so the
 * metadata has to come off explicitly or the shortcut would quietly reintroduce a
 * location leak into a location-based app.
 *
 * <p>Works at the segment level: every APPn and COM segment is dropped and everything
 * else is copied verbatim, so the image is bit-identical and loses no quality.
 */
final class JpegMetadata {

    private static final int MARKER = 0xFF;
    private static final int SOI = 0xD8;   // start of image
    private static final int SOS = 0xDA;   // start of scan — image data follows, stop here
    private static final int APP0 = 0xE0;
    private static final int APP15 = 0xEF;
    private static final int COM = 0xFE;

    private JpegMetadata() {
    }

    /** True if {@code bytes} starts with the JPEG magic number. */
    static boolean isJpeg(byte[] bytes) {
        return bytes.length > 3
                && (bytes[0] & 0xFF) == MARKER
                && (bytes[1] & 0xFF) == SOI
                && (bytes[2] & 0xFF) == MARKER;
    }

    /**
     * Returns {@code jpeg} with all APPn and COM segments removed, or the input
     * unchanged if it cannot be parsed as a segment stream.
     */
    static byte[] strip(byte[] jpeg) {
        if (!isJpeg(jpeg)) {
            return jpeg;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length);
        out.write(jpeg[0]);
        out.write(jpeg[1]);

        int i = 2;
        while (i + 3 < jpeg.length) {
            if ((jpeg[i] & 0xFF) != MARKER) {
                // Not where a segment should start — bail out and keep the original
                // rather than risk emitting a corrupt file.
                return jpeg;
            }
            int marker = jpeg[i + 1] & 0xFF;
            if (marker == SOS) {
                out.write(jpeg, i, jpeg.length - i);
                return out.toByteArray();
            }
            int length = ((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF);
            if (length < 2 || i + 2 + length > jpeg.length) {
                return jpeg;
            }
            boolean isMetadata = (marker >= APP0 && marker <= APP15) || marker == COM;
            if (!isMetadata) {
                out.write(jpeg, i, 2 + length);
            }
            i += 2 + length;
        }
        return jpeg;
    }
}
