package com.platolisto.restaurant_backend.multitenancy;

import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.exception.TenantNotFoundException;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantInterceptorIntegrationTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @InjectMocks
    private TenantInterceptor tenantInterceptor;

    private Restaurant mockRestaurant;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        mockRestaurant = Restaurant.builder()
                .id(42L)
                .name("El Gran Restaurant")
                .subdomain("mario")
                .customDomain("pizzeriamario.com")
                .isActive(true)
                .build();
    }

    @Test
    void shouldResolveTenantUsingXTenantHeader() throws Exception {
        // Given
        request.addHeader("X-Tenant", "mario");
        when(restaurantRepository.findBySubdomainAndIsActiveTrue("mario"))
                .thenReturn(Optional.of(mockRestaurant));

        // When
        boolean result = tenantInterceptor.preHandle(request, response, new Object());

        // Then
        assertThat(result).isTrue();
        assertThat(TenantContext.getCurrentTenant()).isEqualTo(42L);
        verify(restaurantRepository, times(1)).findBySubdomainAndIsActiveTrue("mario");
        verifyNoMoreInteractions(restaurantRepository);
    }

    @Test
    void shouldResolveTenantUsingSubdomainInHost() throws Exception {
        // Given
        request.addHeader("Host", "mario.platolisto.com");
        when(restaurantRepository.findBySubdomainAndIsActiveTrue("mario"))
                .thenReturn(Optional.of(mockRestaurant));

        // When
        boolean result = tenantInterceptor.preHandle(request, response, new Object());

        // Then
        assertThat(result).isTrue();
        assertThat(TenantContext.getCurrentTenant()).isEqualTo(42L);
        verify(restaurantRepository, times(1)).findBySubdomainAndIsActiveTrue("mario");
    }

    @Test
    void shouldResolveTenantUsingCustomDomainInHost() throws Exception {
        // Given
        request.addHeader("Host", "pizzeriamario.com");
        when(restaurantRepository.findByCustomDomainAndIsActiveTrue("pizzeriamario.com"))
                .thenReturn(Optional.of(mockRestaurant));

        // When
        boolean result = tenantInterceptor.preHandle(request, response, new Object());

        // Then
        assertThat(result).isTrue();
        assertThat(TenantContext.getCurrentTenant()).isEqualTo(42L);
        verify(restaurantRepository, times(1)).findByCustomDomainAndIsActiveTrue("pizzeriamario.com");
    }

    @Test
    void shouldThrowTenantNotFoundExceptionWhenTenantNotFoundBySubdomain() {
        // Given
        request.addHeader("X-Tenant", "unknown");
        when(restaurantRepository.findBySubdomainAndIsActiveTrue("unknown"))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> tenantInterceptor.preHandle(request, response, new Object()))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("No se encontró un restaurante activo con el subdominio: unknown");
    }

    @Test
    void shouldThrowTenantNotFoundExceptionWhenHostHeaderIsMissingAndNoXTenantHeader() {
        // When & Then
        assertThatThrownBy(() -> tenantInterceptor.preHandle(request, response, new Object()))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("El encabezado Host es requerido para identificar al restaurante.");
    }

    @Test
    void shouldClearTenantContextAfterCompletion() throws Exception {
        // Given
        TenantContext.setCurrentTenant(42L);

        // When
        tenantInterceptor.afterCompletion(request, response, new Object(), null);

        // Then
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }
}
