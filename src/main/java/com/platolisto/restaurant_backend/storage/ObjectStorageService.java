package com.platolisto.restaurant_backend.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstracción de almacenamiento de objetos (Cloudflare R2 / S3 / local).
 */
public interface ObjectStorageService {

    /**
     * Sube la imagen de un producto y devuelve la URL pública persistible en DB.
     */
    String uploadImage(MultipartFile file, String tenantSlug);

    /**
     * Sube un asset de marca (logo, banner, etc.) bajo {@code tenants/{slug}/{folder}/}.
     */
    String uploadBrandAsset(MultipartFile file, String tenantSlug, String folder);
}
