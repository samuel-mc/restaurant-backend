package com.platolisto.restaurant_backend.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstracción de almacenamiento de objetos (Cloudflare R2 / S3 / local).
 */
public interface ObjectStorageService {

    /**
     * Sube la imagen de un producto y devuelve la URL pública persistible en DB.
     *
     * @param file       imagen (image/*)
     * @param tenantSlug subdominio del restaurante (ej. {@code latrattoria})
     * @return URL pública absoluta del objeto
     */
    String uploadImage(MultipartFile file, String tenantSlug);
}
