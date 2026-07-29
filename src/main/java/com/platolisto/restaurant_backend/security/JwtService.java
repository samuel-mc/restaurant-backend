package com.platolisto.restaurant_backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    public static final String TOKEN_TYPE_WS = "ws";
    public static final String TOKEN_TYPE_STAFF = "staff";
    public static final String TOKEN_TYPE_IMPERSONATION = "impersonation";

    /** Secreto Base64 (mín. 32 bytes decodificados). Sin default inseguro. */
    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration:86400000}") // 1 día en milisegundos
    private long jwtExpiration;

    @Value("${application.security.ws-ticket.expiration-ms:60000}")
    private long wsTicketExpirationMs;

    /** Soporte: token corto al entrar como OWNER/ADMIN de un tenant. */
    @Value("${application.security.impersonation.expiration-ms:1800000}")
    private long impersonationExpirationMs;

    @PostConstruct
    void validateSecretKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "Falta application.security.jwt.secret-key / JWT_SECRET. "
                            + "Define un secreto Base64 de al menos 32 bytes."
            );
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretKey.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "JWT_SECRET debe ser Base64 válido (p. ej. openssl rand -base64 48).",
                    e
            );
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET es demasiado corto: se requieren al menos 32 bytes decodificados."
            );
        }
        if (wsTicketExpirationMs < 5_000L || wsTicketExpirationMs > 300_000L) {
            throw new IllegalStateException(
                    "application.security.ws-ticket.expiration-ms debe estar entre 5s y 5min."
            );
        }
        if (impersonationExpirationMs < 60_000L || impersonationExpirationMs > 7_200_000L) {
            throw new IllegalStateException(
                    "application.security.impersonation.expiration-ms debe estar entre 1min y 2h."
            );
        }
    }

    public long getWsTicketExpirationMs() {
        return wsTicketExpirationMs;
    }

    public long getImpersonationExpirationMs() {
        return impersonationExpirationMs;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails, Long restaurantId, String role) {
        Map<String, Object> extraClaims = new HashMap<>();
        if (restaurantId != null) {
            extraClaims.put("restaurantId", restaurantId);
            extraClaims.put("tenantId", restaurantId);
        }
        extraClaims.put("role", role);
        return generateToken(extraClaims, userDetails);
    }

    /**
     * JWT de miembro del equipo (PIN): incluye {@code staffId}, {@code tenantId} y {@code role}.
     */
    public String generateStaffToken(StaffUserDetails staffDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("restaurantId", staffDetails.getRestaurantId());
        extraClaims.put("tenantId", staffDetails.getRestaurantId());
        extraClaims.put("staffId", staffDetails.getStaffId().toString());
        extraClaims.put("role", staffDetails.getRole().name());
        extraClaims.put("tokenType", TOKEN_TYPE_STAFF);
        return generateToken(extraClaims, staffDetails);
    }

    /**
     * Ticket STOMP de corta vida. Solo autentica WebSocket; no APIs HTTP.
     */
    public String generateWsTicket(String subject, Long restaurantId, String role) {
        Map<String, Object> claims = new HashMap<>();
        if (restaurantId != null) {
            claims.put("restaurantId", restaurantId);
            claims.put("tenantId", restaurantId);
        }
        claims.put("role", role);
        claims.put("tokenType", TOKEN_TYPE_WS);
        claims.put("jti", UUID.randomUUID().toString());
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + wsTicketExpirationMs))
                .signWith(getSignInKey())
                .compact();
    }

    /**
     * JWT de soporte: actúa como OWNER/ADMIN del tenant con TTL corto y auditoría en claims.
     */
    public String generateImpersonationToken(
            UserDetails targetUser,
            Long restaurantId,
            String role,
            String impersonatedBy
    ) {
        if (impersonatedBy == null || impersonatedBy.isBlank()) {
            throw new IllegalArgumentException("Se requiere el SuperAdmin que impersona.");
        }
        if (restaurantId == null) {
            throw new IllegalArgumentException("Se requiere el restaurante a impersonar.");
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("restaurantId", restaurantId);
        claims.put("tenantId", restaurantId);
        claims.put("role", role);
        claims.put("tokenType", TOKEN_TYPE_IMPERSONATION);
        claims.put("impersonatedBy", impersonatedBy.trim().toLowerCase());
        claims.put("jti", UUID.randomUUID().toString());
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(targetUser.getUsername())
                .issuedAt(new Date(now))
                .expiration(new Date(now + impersonationExpirationMs))
                .signWith(getSignInKey())
                .compact();
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    /** Valida firma y expiración sin cargar UserDetails (p. ej. WebSocket CONNECT). */
    public boolean isTokenSignatureValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public Long extractRestaurantId(String token) {
        Claims claims = extractAllClaims(token);
        Object restaurantId = claims.get("restaurantId");
        if (restaurantId instanceof Number) {
            return ((Number) restaurantId).longValue();
        }
        Object tenantId = claims.get("tenantId");
        if (tenantId instanceof Number) {
            return ((Number) tenantId).longValue();
        }
        return null;
    }

    public String extractStaffId(String token) {
        return extractClaim(token, claims -> claims.get("staffId", String.class));
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("tokenType", String.class));
    }

    public String extractJti(String token) {
        Claims claims = extractAllClaims(token);
        Object jti = claims.get("jti");
        if (jti instanceof String s && !s.isBlank()) {
            return s;
        }
        return claims.getId();
    }

    public boolean isWsTicket(String token) {
        return TOKEN_TYPE_WS.equals(extractTokenType(token));
    }

    public boolean isImpersonationToken(String token) {
        return TOKEN_TYPE_IMPERSONATION.equals(extractTokenType(token));
    }

    public String extractImpersonatedBy(String token) {
        return extractClaim(token, claims -> claims.get("impersonatedBy", String.class));
    }

    public boolean isStaffToken(String token) {
        String tokenType = extractTokenType(token);
        if (TOKEN_TYPE_STAFF.equals(tokenType)) {
            return true;
        }
        return StaffUserDetails.isStaffSubject(extractUsername(token));
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
