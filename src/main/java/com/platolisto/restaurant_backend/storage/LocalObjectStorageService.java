package com.platolisto.restaurant_backend.storage;

import com.platolisto.restaurant_backend.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Fallback local cuando R2 está desactivado ({@code cloudflare.r2.enabled=false}).
 */
@Service
@ConditionalOnProperty(
        prefix = "cloudflare.r2",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
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
    public String uploadImage(MultipartFile file, String tenantSlug) {
        return uploadToFolder(file, tenantSlug, "products");
    }

    @Override
    public String uploadBrandAsset(MultipartFile file, String tenantSlug, String folder) {
        return uploadToFolder(file, tenantSlug, sanitizeFolder(folder));
    }

    private String uploadToFolder(MultipartFile file, String tenantSlug, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de imagen está vacío.");
        }
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new IllegalArgumentException("El tenantSlug es requerido para subir la imagen.");
        }

        String contentType = file.getContentType() != null
                ? file.getContentType().toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Formato de imagen no soportado. Usa JPG, PNG, WEBP o GIF."
            );
        }

        String safeName = sanitizeFilename(file.getOriginalFilename());
        String relativeKey = "tenants/%s/%s/%s-%s".formatted(
                tenantSlug.trim().toLowerCase(Locale.ROOT),
                folder,
                UUID.randomUUID(),
                safeName
        );

        Path destination = rootDirectory.resolve(relativeKey).normalize();
        if (!destination.startsWith(rootDirectory)) {
            throw new StorageException("Ruta de almacenamiento inválida.");
        }

        try {
            Files.createDirectories(destination.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new StorageException("No se pudo guardar la imagen.", ex);
        }

        String publicUrl = publicBaseUrl + "/media/" + relativeKey;
        log.info("Imagen guardada localmente: {}", publicUrl);
        return publicUrl;
    }

    private static String sanitizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "brand";
        }
        String cleaned = folder.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return cleaned.isBlank() ? "brand" : cleaned;
    }

    private static String sanitizeFilename(String originalFilename) {
        String name = originalFilename == null || originalFilename.isBlank()
                ? "image.bin"
                : originalFilename.trim();
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return name.isBlank() ? "image.bin" : name;
    }
}
