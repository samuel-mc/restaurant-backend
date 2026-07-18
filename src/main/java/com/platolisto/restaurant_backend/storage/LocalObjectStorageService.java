package com.platolisto.restaurant_backend.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Implementación local "R2-ready": misma interfaz que usará Cloudflare R2.
 * Persiste en disco y expone URL bajo {@code /media/**}.
 */
@Service
@Slf4j
public class LocalObjectStorageService implements ObjectStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final Path rootDirectory;
    private final String publicBaseUrl;

    public LocalObjectStorageService(
            @Value("${application.storage.local-root:uploads}") String localRoot,
            @Value("${application.storage.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) throws IOException {
        this.rootDirectory = Path.of(localRoot).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        Files.createDirectories(this.rootDirectory);
        log.info("Almacenamiento local de imágenes en {}", this.rootDirectory);
    }

    @Override
    public String uploadProductImage(Long restaurantId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de imagen está vacío.");
        }

        String contentType = file.getContentType() != null
                ? file.getContentType().toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Formato de imagen no soportado. Usa JPG, PNG, WEBP o GIF."
            );
        }

        String extension = extensionFor(contentType, file.getOriginalFilename());
        String relativeKey = "tenants/%d/products/%s%s".formatted(
                restaurantId,
                UUID.randomUUID(),
                extension
        );

        Path destination = rootDirectory.resolve(relativeKey).normalize();
        if (!destination.startsWith(rootDirectory)) {
            throw new IllegalStateException("Ruta de almacenamiento inválida.");
        }

        try {
            Files.createDirectories(destination.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar la imagen del producto.", ex);
        }

        String publicUrl = publicBaseUrl + "/media/" + relativeKey;
        log.info("Imagen de producto guardada: {}", publicUrl);
        return publicUrl;
    }

    private static String extensionFor(String contentType, String originalFilename) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> {
                if (originalFilename != null && originalFilename.contains(".")) {
                    String ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
                    yield ext.toLowerCase(Locale.ROOT);
                }
                yield ".bin";
            }
        };
    }
}
