package com.platolisto.restaurant_backend.storage;

import com.platolisto.restaurant_backend.exception.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Almacenamiento definitivo de imágenes en Cloudflare R2 (S3-compatible).
 */
@Service
@Primary
@ConditionalOnProperty(prefix = "cloudflare.r2", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CloudflareR2StorageService implements ObjectStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final S3Client r2S3Client;

    @Value("${cloudflare.r2.bucket-name}")
    private String bucketName;

    @Value("${cloudflare.r2.public-url}")
    private String publicUrl;

    @Override
    public String uploadImage(MultipartFile file, String tenantSlug) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de imagen está vacío.");
        }

        String slug = requireTenantSlug(tenantSlug);
        String contentType = resolveContentType(file);
        String objectKey = buildObjectKey(slug, file.getOriginalFilename());

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(contentType)
                .contentLength(file.getSize())
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            r2S3Client.putObject(
                    putRequest,
                    RequestBody.fromInputStream(inputStream, file.getSize())
            );
        } catch (S3Exception ex) {
            log.error(
                    "Error S3/R2 al subir imagen [bucket={}, key={}, status={}]: {}",
                    bucketName,
                    objectKey,
                    ex.statusCode(),
                    ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorMessage() : ex.getMessage()
            );
            throw new StorageException(
                    "No se pudo subir la imagen a Cloudflare R2.",
                    ex
            );
        } catch (IOException ex) {
            log.error("No se pudo leer el MultipartFile para R2 [key={}]: {}", objectKey, ex.getMessage());
            throw new StorageException("No se pudo leer el archivo de imagen.", ex);
        }

        String url = joinPublicUrl(publicUrl, objectKey);
        log.info("Imagen subida a R2: {}", url);
        return url;
    }

    private static String requireTenantSlug(String tenantSlug) {
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new IllegalArgumentException("El tenantSlug es requerido para subir la imagen.");
        }
        return tenantSlug.trim().toLowerCase(Locale.ROOT);
    }

    private static String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType() != null
                ? file.getContentType().toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Formato de imagen no soportado. Usa JPG, PNG, WEBP o GIF."
            );
        }
        return contentType;
    }

    /**
     * Clave ordenada: {@code tenants/{slug}/products/{uuid}-{safeOriginalName}}.
     */
    private static String buildObjectKey(String tenantSlug, String originalFilename) {
        String safeName = sanitizeFilename(originalFilename);
        return "tenants/%s/products/%s-%s".formatted(tenantSlug, UUID.randomUUID(), safeName);
    }

    private static String sanitizeFilename(String originalFilename) {
        String name = originalFilename == null || originalFilename.isBlank()
                ? "image.bin"
                : originalFilename.trim();

        // Evita path traversal y caracteres raros en la key.
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.isBlank()) {
            name = "image.bin";
        }
        return name;
    }

    private static String joinPublicUrl(String baseUrl, String objectKey) {
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        if (base.isBlank()) {
            throw new StorageException("Falta cloudflare.r2.public-url.");
        }
        return base + "/" + objectKey;
    }
}
