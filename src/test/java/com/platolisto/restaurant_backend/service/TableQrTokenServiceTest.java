package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableQrTokenServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    private TableQrTokenService tableQrTokenService;

    @BeforeEach
    void setUp() {
        tableQrTokenService = new TableQrTokenService(restaurantRepository, 180, true);
    }

    @Test
    void signAndVerifyRoundTrip() {
        Restaurant restaurant = Restaurant.builder().id(1L).name("Test").subdomain("test").build();
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        String token = tableQrTokenService.sign(restaurant, "4");
        assertTrue(token.contains("."));
        assertTrue(tableQrTokenService.verify(restaurant, "4", token));
        assertFalse(tableQrTokenService.verify(restaurant, "5", token));
        assertFalse(tableQrTokenService.verify(restaurant, "4", "deadbeef"));
    }

    @Test
    void differentTablesGetDifferentTokens() {
        Restaurant restaurant = Restaurant.builder().id(1L).name("Test").subdomain("test").build();
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        String t4 = tableQrTokenService.sign(restaurant, "4");
        String t5 = tableQrTokenService.sign(restaurant, "5");
        assertNotEquals(t4, t5);

        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(captor.capture());
        assertTrue(captor.getValue().getTableQrSecret() != null);
    }

    @Test
    void rejectsExpiredToken() {
        Restaurant restaurant = Restaurant.builder().id(1L).name("Test").subdomain("test").build();
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant ancient = Instant.now().minus(200, ChronoUnit.DAYS);
        String token = tableQrTokenService.sign(restaurant, "4", ancient);
        assertFalse(tableQrTokenService.verify(restaurant, "4", token));
    }

    @Test
    void acceptsLegacyWhenEnabledAndRejectsWhenDisabled() {
        Restaurant restaurant = Restaurant.builder().id(1L).name("Test").subdomain("test").build();
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        String legacy = tableQrTokenService.signLegacyV1ForTests(restaurant, "4");
        assertTrue(tableQrTokenService.verify(restaurant, "4", legacy));

        TableQrTokenService strict = new TableQrTokenService(restaurantRepository, 180, false);
        assertFalse(strict.verify(restaurant, "4", legacy));
    }
}
