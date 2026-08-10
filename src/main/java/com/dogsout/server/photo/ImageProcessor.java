package com.dogsout.server.photo;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Turns an arbitrary uploaded image into the fixed set of {@link PhotoRendition}
 * JPEGs that get stored.
 *
 * <p>Everything is re-encoded rather than passed through. That is what makes the
 * size guarantees hold no matter what the client sends, and it also <b>strips
 * EXIF metadata</b> — phone photos carry GPS coordinates, and this app already
 * knows roughly where its users are; publishing the exact coordinates of where
 * someone photographed their dog would be a considerably worse leak.
 * ImageIO's JPEG writer emits no metadata unless explicitly asked to, so the
 * stripping is a property of re-encoding, not a separate step.
 */
@Component
public class ImageProcessor {

    /** Refuse anything larger before decoding: a decoded pixel buffer is ~4 bytes/px. */
    private static final int MAX_UPLOAD_BYTES = 15 * 1024 * 1024;

    /**
     * Largest image accepted, checked from the header before any pixels are decoded.
     *
     * <p>25 MP is a 100 MB pixel buffer. The production JVM runs with {@code -Xmx400m},
     * so this cannot be raised much without a single upload being able to OOM the
     * server for everyone — and the endpoint is reachable by any signed-in user. It is
     * far above anything the app itself sends, since the client downscales to 2160px
     * (~4.6 MP) before uploading.
     */
    private static final long MAX_PIXELS = 25_000_000L;

    /**
     * Decodes {@code source} once and renders every rendition from it.
     *
     * @throws ResponseStatusException 400 if the bytes are not a decodable image
     */
    public Map<PhotoRendition, byte[]> process(byte[] source) {
        if (source == null || source.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image is empty");
        }
        if (source.length > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image is too large");
        }

        BufferedImage decoded = flattenTransparency(decode(source));

        Map<PhotoRendition, byte[]> out = new EnumMap<>(PhotoRendition.class);
        for (PhotoRendition rendition : PhotoRendition.values()) {
            out.put(rendition, best(source, decoded, rendition));
        }
        return out;
    }

    /**
     * Returns whichever is smaller: the re-encoded rendition, or the original bytes.
     *
     * <p>Re-encoding an already-compressed JPEG at a higher quality than it was saved
     * with makes it <em>bigger</em> while also losing a generation of quality. That is
     * not a rare edge case here — the app resizes before uploading, so a photo that
     * already fits the target box is the normal input, and without this check every
     * upload would inflate.
     *
     * <p>The original is only eligible when it is a JPEG that already fits inside the
     * rendition's box, and its metadata is stripped first — see {@link JpegMetadata}.
     */
    private byte[] best(byte[] source, BufferedImage decoded, PhotoRendition rendition) {
        byte[] rendered = render(decoded, rendition);
        boolean fitsInBox = decoded.getWidth() <= rendition.maxWidth()
                && decoded.getHeight() <= rendition.maxHeight();
        if (!fitsInBox || !JpegMetadata.isJpeg(source)) {
            return rendered;
        }
        byte[] stripped = JpegMetadata.strip(source);
        return stripped.length < rendered.length ? stripped : rendered;
    }

    /**
     * Decodes the image, reading its dimensions from the header first.
     *
     * <p>The size check has to happen before any pixels are read. {@code ImageIO.read}
     * allocates the whole pixel buffer up front, so checking the dimensions of the
     * decoded result is too late to prevent the allocation it is meant to prevent — a
     * 100 MP image would already have taken 400 MB of heap, the JVM's entire budget.
     */
    private BufferedImage decode(byte[] source) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                // ImageIO has no reader for HEIC. The client transcodes to JPEG before
                // upload, so this is a malformed or unsupported upload.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image format");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) {
                    throw new ResponseStatusException(
                            HttpStatus.PAYLOAD_TOO_LARGE, "Image resolution is too large");
                }
                // Decoded at full resolution: at the 25 MP ceiling that is a 100 MB
                // buffer, one image at a time, which the heap absorbs. Subsampling
                // during decode would cut that further but costs visible quality, and
                // photos are the product here.
                return reader.read(0, reader.getDefaultReadParam());
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read image", e);
        }
    }

    /**
     * Composites an image with an alpha channel onto white.
     *
     * <p>JPEG has no transparency, so without this the alpha is simply dropped and
     * every transparent pixel reads as black — a PNG with a transparent background
     * arrives as a black rectangle. Painting white first is what a viewer expects.
     */
    private BufferedImage flattenTransparency(BufferedImage source) {
        if (!source.getColorModel().hasAlpha()) {
            return source;
        }
        BufferedImage flattened = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = flattened.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, source.getWidth(), source.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return flattened;
    }

    private byte[] render(BufferedImage decoded, PhotoRendition rendition) {
        // Clamping the box to the source dimensions is what stops Thumbnailator
        // from scaling up — left alone, .size() happily blows a 200px upload up to
        // 1080px, producing a blurry image in a much larger file.
        int boxWidth = Math.min(rendition.maxWidth(), decoded.getWidth());
        int boxHeight = Math.min(rendition.maxHeight(), decoded.getHeight());
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(decoded)
                    .size(boxWidth, boxHeight)
                    .keepAspectRatio(true)
                    .outputQuality(rendition.quality())
                    .outputFormat("jpg")
                    // JPEG has no alpha; without this, transparent PNGs come out black.
                    .imageType(BufferedImage.TYPE_INT_RGB)
                    .toOutputStream(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not process image", e);
        }
    }
}
