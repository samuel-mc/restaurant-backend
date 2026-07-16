package com.platolisto.restaurant_backend.security;

import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        userDetails = new User("admin@platolisto.com", "password", Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void shouldAuthenticateValidTokenAndSameTenant() throws ServletException, IOException {
        // Given
        request.addHeader("Authorization", "Bearer valid-token");
        when(jwtService.extractUsername("valid-token")).thenReturn("admin@platolisto.com");
        when(userDetailsService.loadUserByUsername("admin@platolisto.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("valid-token", userDetails)).thenReturn(true);
        
        // Tenant checks match
        TenantContext.setCurrentTenant(1L);
        when(jwtService.extractRestaurantId("valid-token")).thenReturn(1L);
        when(jwtService.extractRole("valid-token")).thenReturn("ADMIN");

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("admin@platolisto.com");
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void shouldDenyAccessWhenTenantMismatch() throws ServletException, IOException {
        // Given
        request.addHeader("Authorization", "Bearer cross-tenant-token");
        when(jwtService.extractUsername("cross-tenant-token")).thenReturn("admin@platolisto.com");
        when(userDetailsService.loadUserByUsername("admin@platolisto.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("cross-tenant-token", userDetails)).thenReturn(true);
        
        // El tenant activo es 1L, pero el token pertenece al restaurante 2L
        TenantContext.setCurrentTenant(1L);
        when(jwtService.extractRestaurantId("cross-tenant-token")).thenReturn(2L);
        when(jwtService.extractRole("cross-tenant-token")).thenReturn("ADMIN");

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentAsString()).contains("Acceso no autorizado a este restaurante.");
        verifyNoInteractions(filterChain); // El flujo no continúa en la cadena
    }

    @Test
    void shouldAllowAccessForSuperAdminEvenWithTenantMismatch() throws ServletException, IOException {
        // Given
        request.addHeader("Authorization", "Bearer superadmin-token");
        when(jwtService.extractUsername("superadmin-token")).thenReturn("super@platolisto.com");
        when(userDetailsService.loadUserByUsername("super@platolisto.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("superadmin-token", userDetails)).thenReturn(true);
        
        // El tenant activo es 1L, el token no lo tiene (e.g. null), pero el usuario es SUPER_ADMIN
        TenantContext.setCurrentTenant(1L);
        when(jwtService.extractRestaurantId("superadmin-token")).thenReturn(null);
        when(jwtService.extractRole("superadmin-token")).thenReturn("SUPER_ADMIN");

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void shouldContinueChainWithoutAuthenticationIfNoAuthHeader() throws ServletException, IOException {
        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
