package com.platolisto.restaurant_backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class JwtDenylistServiceTest {

    private JwtService jwtService;
    private JwtDenylistService denylistService;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "AURb/z6vKSd0kHvQY/EnUgUEOaU1oZW5CiuuroWPdDwKyBBYWF4H2OkYR0hYXUhp");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 28_800_000L);
        ReflectionTestUtils.setField(jwtService, "wsTicketExpirationMs", 60_000L);
        ReflectionTestUtils.setField(jwtService, "impersonationExpirationMs", 1_800_000L);
        denylistService = new JwtDenylistService(jwtService);
        userDetails = new User("admin@platolisto.com", "password", Collections.emptyList());
    }

    @Test
    void revokeMakesTokenRevoked() {
        String token = jwtService.generateToken(userDetails, 1L, "ADMIN");
        assertThat(denylistService.isRevoked(token)).isFalse();

        denylistService.revoke(token);

        assertThat(denylistService.isRevoked(token)).isTrue();
        assertThat(denylistService.isJtiRevoked(jwtService.extractJti(token))).isTrue();
    }

    @Test
    void differentTokenNotAffected() {
        String a = jwtService.generateToken(userDetails, 1L, "ADMIN");
        String b = jwtService.generateToken(userDetails, 1L, "ADMIN");
        denylistService.revoke(a);
        assertThat(denylistService.isRevoked(b)).isFalse();
    }
}
