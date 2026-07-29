package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Firma y verifica tokens de acceso de mesa embebidos en el QR ({@code ?m=&t=}).
 */
@Service
@RequiredArgsConstructor
public class TableQrTokenService {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final int SECRET_BYTES = 32;
    private static final int TOKEN_HEX_CHARS = 32; // 16 bytes del MAC

    private final RestaurantRepository restaurantRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String sign(Restaurant restaurant, String tableNumber) {
        String table = normalizeTable(tableNumber);
        if (table == null) {
            throw new IllegalArgumentException("El número de mesa es requerido.");
        }
        String secret = ensureSecret(restaurant);
        return hmacHex(secret, payload(table));
    }

    public boolean verify(Restaurant restaurant, String tableNumber, String tableToken) {
        if (tableToken == null || tableToken.isBlank()) {
            return false;
        }
        String table = normalizeTable(tableNumber);
        if (table == null || restaurant.getTableQrSecret() == null || restaurant.getTableQrSecret().isBlank()) {
            return false;
        }
        String expected = hmacHex(restaurant.getTableQrSecret().trim(), payload(table));
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                tableToken.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        );
    }

    public void requireValid(Restaurant restaurant, String tableNumber, String tableToken) {
        if (!verify(restaurant, tableNumber, tableToken)) {
            throw new IllegalArgumentException(
                    "Código de mesa inválido o incompleto. Escanea el código QR de tu mesa."
            );
        }
    }

    private String ensureSecret(Restaurant restaurant) {
        String existing = restaurant.getTableQrSecret();
        if (existing != null && !existing.isBlank()) {
            return existing.trim();
        }
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        String generated = Base64.getEncoder().encodeToString(bytes);
        restaurant.setTableQrSecret(generated);
        restaurantRepository.save(restaurant);
        return generated;
    }

    private static String payload(String normalizedTable) {
        return "v1|" + normalizedTable;
    }

    private static String hmacHex(String secretBase64, String payload) {
        try {
            byte[] key = Base64.getDecoder().decode(secretBase64);
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(key, HMAC_ALG));
            byte[] full = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] truncated = new byte[TOKEN_HEX_CHARS / 2];
            System.arraycopy(full, 0, truncated, 0, truncated.length);
            return HexFormat.of().formatHex(truncated);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token de mesa.", e);
        }
    }

    public static String normalizeTable(String tableNumber) {
        if (tableNumber == null) {
            return null;
        }
        String trimmed = tableNumber.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
