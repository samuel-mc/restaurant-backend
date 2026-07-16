package com.platolisto.restaurant_backend.multitenancy;

import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantFilterTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private TenantFilter tenantFilter;

    private Restaurant mockRestaurant;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setRequestURI("/api/v1/products"); // Debe pasar el filtro de path /api/**

        mockRestaurant = Restaurant.builder()
                .id(42L)
                .name("El Gran Restaurant")
                .subdomain("mario")
                .customDomain("pizzeriamario.com")
                .isActive(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldResolveTenantUsingXTenantHeader() throws Exception {
        // Given
        request.addHeader("X-Tenant", "mario");
        when(restaurantRepository.findBySubdomainAndIsActiveTrue("mario"))
                .thenReturn(Optional.of(mockRestaurant));

        // When
        tenantFilter.doFilter(request, response, filterChain);

        // Then
        assertThat(TenantContext.getCurrentTenant()).isNull(); // El contexto se limpia al final en el finally block
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void shouldResolveTenantUsingCustomDomain() throws Exception {
        // Given
        request.addHeader("Host", "pizzeriamario.com");
        when(restaurantRepository.findByCustomDomainAndIsActiveTrue("pizzeriamario.com"))
                .thenReturn(Optional.of(mockRestaurant));

        // When
        tenantFilter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void shouldResolveTenantUsingSubdomain() throws Exception {
        // Given
        request.addHeader("Host", "mario.platolisto.com");
        when(restaurantRepository.findByCustomDomainAndIsActiveTrue("mario.platolisto.com"))
                .thenReturn(Optional.empty());
        when(restaurantRepository.findBySubdomainAndIsActiveTrue("mario"))
                .thenReturn(Optional.of(mockRestaurant));

        // When
        tenantFilter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void shouldReturn404WhenTenantNotFound() throws Exception {
        // Given
        request.addHeader("X-Tenant", "nonexistent");
        when(restaurantRepository.findBySubdomainAndIsActiveTrue("nonexistent"))
                .thenReturn(Optional.empty());

        // When
        tenantFilter.doFilter(request, response, filterChain);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        assertThat(response.getContentAsString()).contains("No se encontró un restaurante activo");
        verifyNoInteractions(filterChain); // No continúa la cadena
    }
}
