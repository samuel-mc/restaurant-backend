package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Firma y verifica tokens de acceso de mesa embebidos en el QR ({@code ?m=&t=}).
 * Formato v2: {@code {expiresAtEpochSeconds}.{hmacHex}} — caducan sin Redis.
 * Formato v1 (solo hex): opcional vía {@code allow-legacy-tokens}.
 */
@Service
public class TableQrTokenService {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final int SECRET_BYTES = 32;
    private static final int TOKEN_HEX_CHARS = 32; // 16 bytes del MAC
    private static final long MIN_TTL_DAYS = 1;
    private static final long MAX_TTL_DAYS = 730;

    private final RestaurantRepository restaurantRepository;
    private final long ttlSeconds;
    private final boolean allowLegacyTokens;
    private final SecureRandom secureRandom = new SecureRandom();

    public TableQrTokenService(
            RestaurantRepository restaurantRepository,
            @Value("${application.security.table-qr.ttl-days:180}") long ttlDays,
            @Value("${application.security.table-qr.allow-legacy-tokens:false}") boolean allowLegacyTokens
    ) {
        if (ttlDays < MIN_TTL_DAYS || ttlDays > MAX_TTL_DAYS) {
            throw new IllegalArgumentException(
                    "application.security.table-qr.ttl-days debe estar entre "
                            + MIN_TTL_DAYS + " y " + MAX_TTL_DAYS + "."
            );
        }
        this.restaurantRepository = restaurantRepository;
        this.ttlSeconds = TimeUnit.DAYS.toSeconds(ttlDays);
        this.allowLegacyTokens = allowLegacyTokens;
    }

    @Transactional
    public String sign(Restaurant restaurant, String tableNumber) {
        return sign(restaurant, tableNumber, Instant.now());
    }

    /**
     * @return token v2 y epoch de expiración (segundos)
     */
    @Transactional
    public SignedTableToken signWithExpiry(Restaurant restaurant, String tableNumber) {
        Instant now = Instant.now();
        long expiresAt = now.getEpochSecond() + ttlSeconds;
        String token = signAt(restaurant, tableNumber, expiresAt);
        return new SignedTableToken(token, Instant.ofEpochSecond(expiresAt));
    }

    @Transactional
    String sign(Restaurant restaurant, String tableNumber, Instant now) {
        long expiresAt = now.getEpochSecond() + ttlSeconds;
        return signAt(restaurant, tableNumber, expiresAt);
    }

    /** Token v1 sin caducidad — solo para tests de compatibilidad. */
    String signLegacyV1ForTests(Restaurant restaurant, String tableNumber) {
        String table = normalizeTable(tableNumber);
        if (table == null) {
            throw new IllegalArgumentException("El número de mesa es requerido.");
        }
        String secret = ensureSecret(restaurant);
        return hmacHex(secret, payloadV1(table));
    }

    private String signAt(Restaurant restaurant, String tableNumber, long expiresAtEpochSeconds) {
        String table = normalizeTable(tableNumber);
        if (table == null) {
            throw new IllegalArgumentException("El número de mesa es requerido.");
        }
        String secret = ensureSecret(restaurant);
        String mac = hmacHex(secret, payloadV2(table, expiresAtEpochSeconds));
        return expiresAtEpochSeconds + "." + mac;
    }

    public boolean verify(Restaurant restaurant, String tableNumber, String tableToken) {
        if (tableToken == null || tableToken.isBlank()) {
            return false;
        }
        String table = normalizeTable(tableNumber);
        if (table == null || restaurant.getTableQrSecret() == null || restaurant.getTableQrSecret().isBlank()) {
            return false;
        }
        String secret = restaurant.getTableQrSecret().trim();
        String raw = tableToken.trim();

        int dot = raw.indexOf('.');
        if (dot > 0 && dot < raw.length() - 1) {
            String expPart = raw.substring(0, dot);
            String macPart = raw.substring(dot + 1).toLowerCase(Locale.ROOT);
            long expiresAt;
            try {
                expiresAt = Long.parseLong(expPart);
            } catch (NumberFormatException e) {
                return false;
            }
            if (expiresAt <= Instant.now().getEpochSecond()) {
                return false;
            }
            String expected = hmacHex(secret, payloadV2(table, expiresAt));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    macPart.getBytes(StandardCharsets.UTF_8)
            );
        }

        // Legacy v1: solo hex del MAC (sin caducidad). Desactivar en prod tras reimprimir QRs.
        if (!allowLegacyTokens) {
            return false;
        }
        if (!raw.matches("[0-9a-fA-F]{" + TOKEN_HEX_CHARS + "}")) {
            return false;
        }
        String expectedLegacy = hmacHex(secret, payloadV1(table));
        return MessageDigest.isEqual(
                expectedLegacy.getBytes(StandardCharsets.UTF_8),
                raw.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        );
    }

    public void requireValid(Restaurant restaurant, String tableNumber, String tableToken) {
        if (!verify(restaurant, tableNumber, tableToken)) {
            throw new IllegalArgumentException(
                    "Código de mesa inválido, incompleto o caducado. Escanea el código QR de tu mesa."
            );
        }
    }

    public long ttlSeconds() {
        return ttlSeconds;
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

    private static String payloadV1(String normalizedTable) {
        return "v1|" + normalizedTable;
    }

    private static String payloadV2(String normalizedTable, long expiresAtEpochSeconds) {
        return "v2|" + normalizedTable + "|" + expiresAtEpochSeconds;
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

    public record SignedTableToken(String token, Instant expiresAt) {}
}
