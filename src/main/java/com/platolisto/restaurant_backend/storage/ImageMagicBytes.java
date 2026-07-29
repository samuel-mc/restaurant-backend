package com.platolisto.restaurant_backend.storage;

import java.util.Locale;

/**
 * Detecta tipo de imagen por magic bytes (no confiar en Content-Type del cliente).
 */
public final class ImageMagicBytes {

    public record DetectedImage(String contentType, String extension) {}

    private ImageMagicBytes() {}

    public static DetectedImage detect(byte[] header) {
        if (header == null || header.length < 3) {
            return null;
        }
        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return new DetectedImage("image/jpeg", "jpg");
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return new DetectedImage("image/png", "png");
        }
        // GIF87a / GIF89a
        if (header.length >= 6
                && header[0] == 'G'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == '8'
                && (header[4] == '7' || header[4] == '9')
                && header[5] == 'a') {
            return new DetectedImage("image/gif", "gif");
        }
        // WebP: RIFF....WEBP
        if (header.length >= 12
                && header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P') {
            return new DetectedImage("image/webp", "webp");
        }
        return null;
    }

    public static String sanitizeFilename(String originalFilename, String extension) {
        String name = originalFilename == null || originalFilename.isBlank()
                ? "image"
                : originalFilename.trim();
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.isBlank()) {
            name = "image";
        }
        String ext = extension == null ? "bin" : extension.toLowerCase(Locale.ROOT);
        return name + "." + ext;
    }
}
