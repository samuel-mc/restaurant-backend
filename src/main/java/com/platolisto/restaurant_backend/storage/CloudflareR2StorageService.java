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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
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

    private final S3Client r2S3Client;

    @Value("${cloudflare.r2.bucket-name}")
    private String bucketName;

    @Value("${cloudflare.r2.public-url}")
    private String publicUrl;

    @Override
    public String uploadImage(MultipartFile file, String tenantSlug) {
        return uploadToFolder(file, tenantSlug, "products");
    }

    @Override
    public String uploadBrandAsset(MultipartFile file, String tenantSlug, String folder) {
        String safeFolder = sanitizeFolder(folder);
        return uploadToFolder(file, tenantSlug, safeFolder);
    }

    private String uploadToFolder(MultipartFile file, String tenantSlug, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de imagen está vacío.");
        }

        String slug = requireTenantSlug(tenantSlug);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new StorageException("No se pudo leer el archivo de imagen.", ex);
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("El archivo de imagen está vacío.");
        }

        ImageMagicBytes.DetectedImage detected = ImageMagicBytes.detect(bytes);
        if (detected == null) {
            throw new IllegalArgumentException(
                    "Formato de imagen no soportado. Usa JPG, PNG, WEBP o GIF."
            );
        }

        String objectKey = "tenants/%s/%s/%s-%s".formatted(
                slug,
                folder,
                UUID.randomUUID(),
                ImageMagicBytes.sanitizeFilename(file.getOriginalFilename(), detected.extension())
        );

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(detected.contentType())
                .contentLength((long) bytes.length)
                .build();

        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            r2S3Client.putObject(
                    putRequest,
                    RequestBody.fromInputStream(inputStream, bytes.length)
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

    private static String sanitizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "brand";
        }
        String cleaned = folder.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return cleaned.isBlank() ? "brand" : cleaned;
    }

    private static String joinPublicUrl(String baseUrl, String objectKey) {
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        if (base.isBlank()) {
            throw new StorageException("Falta cloudflare.r2.public-url.");
        }
        return base + "/" + objectKey;
    }
}
