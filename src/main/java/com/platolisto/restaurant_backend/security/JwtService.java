package com.platolisto.restaurant_backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    // Secreta por defecto de 256 bits codificada en hexadecimal
    @Value("${application.security.jwt.secret-key:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String secretKey;

    @Value("${application.security.jwt.expiration:86400000}") // 1 día en milisegundos
    private long jwtExpiration;

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
        extraClaims.put("tokenType", "staff");
        return generateToken(extraClaims, staffDetails);
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

    public Long extractRestaurantId(String token) {
        // En JWT, los números en JSON a menudo se extraen como Integer o Long.
        // Convertir de forma segura para evitar ClassCastException.
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

    public boolean isStaffToken(String token) {
        String tokenType = extractClaim(token, claims -> claims.get("tokenType", String.class));
        if ("staff".equals(tokenType)) {
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
