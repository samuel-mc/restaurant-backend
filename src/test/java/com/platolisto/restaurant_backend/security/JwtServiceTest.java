package com.platolisto.restaurant_backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Inyectamos valores que normalmente resuelve Spring mediante @Value
        ReflectionTestUtils.setField(jwtService, "secretKey", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400000L); // 1 día

        userDetails = new User("admin@platolisto.com", "password", Collections.emptyList());
    }

    @Test
    void shouldGenerateAndExtractToken() {
        // When
        String token = jwtService.generateToken(userDetails, 1L, "ADMIN");

        // Then
        assertThat(token).isNotEmpty();
        assertThat(jwtService.extractUsername(token)).isEqualTo("admin@platolisto.com");
        assertThat(jwtService.extractRestaurantId(token)).isEqualTo(1L);
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void shouldValidateCorrectToken() {
        // Given
        String token = jwtService.generateToken(userDetails, 1L, "ADMIN");

        // When
        boolean isValid = jwtService.isTokenValid(token, userDetails);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    void shouldFailValidationForAlteredToken() {
        // Given
        String token = jwtService.generateToken(userDetails, 1L, "ADMIN");
        String alteredToken = token + "xyz";

        // When & Then
        assertThatThrownBy(() -> jwtService.isTokenValid(alteredToken, userDetails))
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldDetectExpiredToken() {
        // Given: Configurar expiración en negativo para que expire de inmediato al crearse
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000L);
        String token = jwtService.generateToken(userDetails, 1L, "ADMIN");

        // When & Then
        assertThatThrownBy(() -> jwtService.isTokenValid(token, userDetails))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }
}
