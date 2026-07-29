package com.platolisto.restaurant_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImpersonationReadOnlyFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private ImpersonationReadOnlyFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void allowsGetForImpersonation() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/v1/admin/products");
        request.addHeader("Authorization", "Bearer imp-token");
        when(jwtService.isImpersonationToken("imp-token")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void blocksPatchForImpersonation() throws ServletException, IOException {
        request.setMethod("PATCH");
        request.setRequestURI("/api/v1/admin/orders/abc/status");
        request.addHeader("Authorization", "Bearer imp-token");
        when(jwtService.isImpersonationToken("imp-token")).thenReturn(true);
        when(jwtService.extractImpersonatedBy("imp-token")).thenReturn("sa@platolisto.com");

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentAsString()).contains("solo lectura");
    }

    @Test
    void allowsLogoutAndWsTicket() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/auth/logout");
        request.addHeader("Authorization", "Bearer imp-token");
        when(jwtService.isImpersonationToken("imp-token")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);

        reset(filterChain);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/admin/ws-ticket");
        request.addHeader("Authorization", "Bearer imp-token");
        when(jwtService.isImpersonationToken("imp-token")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void ignoresNonImpersonationTokens() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/admin/team/1");
        request.addHeader("Authorization", "Bearer normal");
        when(jwtService.isImpersonationToken("normal")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
