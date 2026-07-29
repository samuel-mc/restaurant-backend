package com.platolisto.restaurant_backend.security;

import com.platolisto.restaurant_backend.service.WsTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WsTicketServiceTest {

    private JwtService jwtService;
    private WsTicketService wsTicketService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        byte[] key = new byte[48];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (i + 1);
        }
        ReflectionTestUtils.setField(jwtService, "secretKey", Base64.getEncoder().encodeToString(key));
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86_400_000L);
        ReflectionTestUtils.setField(jwtService, "wsTicketExpirationMs", 60_000L);
        ReflectionTestUtils.invokeMethod(jwtService, "validateSecretKey");
        wsTicketService = new WsTicketService(jwtService);
    }

    @Test
    void consumesTicketOnlyOnce() {
        String ticket = jwtService.generateWsTicket("kitchen@test", 1L, "COCINA");
        assertDoesNotThrow(() -> wsTicketService.authenticateAndConsume(ticket));
        assertThrows(
                IllegalArgumentException.class,
                () -> wsTicketService.authenticateAndConsume(ticket)
        );
    }

    @Test
    void rejectsSessionJwt() {
        var user = org.springframework.security.core.userdetails.User.withUsername("admin@test")
                .password("n/a")
                .roles("ADMIN")
                .build();
        String session = jwtService.generateToken(user, 1L, "ADMIN");
        assertThrows(
                IllegalArgumentException.class,
                () -> wsTicketService.authenticateAndConsume(session)
        );
    }
}
