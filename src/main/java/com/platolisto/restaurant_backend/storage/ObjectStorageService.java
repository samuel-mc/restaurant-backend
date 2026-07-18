package com.platolisto.restaurant_backend.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstracción de almacenamiento de objetos (R2 / S3 / local).
 * La implementación actual es local; R2 reemplazará el bean sin tocar controladores.
 */
public interface ObjectStorageService {

    /**
     * Sube la imagen de un producto y devuelve la URL pública persistible en DB.
     *
     * @param restaurantId tenant dueño del archivo
     * @param file         imagen (image/*)
     * @return URL pública (absoluta o relativa servible)
     */
    String uploadProductImage(Long restaurantId, MultipartFile file);
}
