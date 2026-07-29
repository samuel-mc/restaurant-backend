package com.platolisto.restaurant_backend.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageMagicBytesTest {

    @Test
    void detectsJpeg() {
        byte[] header = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        ImageMagicBytes.DetectedImage detected = ImageMagicBytes.detect(header);
        assertNotNull(detected);
        assertEquals("image/jpeg", detected.contentType());
    }

    @Test
    void detectsPng() {
        byte[] header = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        };
        ImageMagicBytes.DetectedImage detected = ImageMagicBytes.detect(header);
        assertNotNull(detected);
        assertEquals("image/png", detected.contentType());
    }

    @Test
    void rejectsUnknown() {
        assertNull(ImageMagicBytes.detect(new byte[] {0x00, 0x01, 0x02, 0x03}));
    }
}
