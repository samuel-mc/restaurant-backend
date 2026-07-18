package com.platolisto.restaurant_backend.exception;

/**
 * Error de almacenamiento de objetos (R2 / S3 / local).
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
