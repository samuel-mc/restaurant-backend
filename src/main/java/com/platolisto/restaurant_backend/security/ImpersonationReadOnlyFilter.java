package com.platolisto.restaurant_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Sesión de soporte ({@code tokenType=impersonation}): solo lectura.
 * Permite GET/HEAD/OPTIONS, logout y ticket WS (observar cocina en vivo).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImpersonationReadOnlyFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        try {
            if (!jwtService.isImpersonationToken(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isReadSafe(request) || isAllowedWrite(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String actor = null;
        try {
            actor = jwtService.extractImpersonatedBy(jwt);
        } catch (Exception ignored) {
            // best-effort audit
        }
        log.warn(
                "Mutación bloqueada en sesión de soporte: {} {} (actor={})",
                request.getMethod(),
                request.getRequestURI(),
                actor
        );
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"error\":\"Sesión de soporte en solo lectura. No se pueden guardar cambios.\"}"
        );
    }

    private static boolean isReadSafe(HttpServletRequest request) {
        String method = request.getMethod();
        return HttpMethod.GET.matches(method)
                || HttpMethod.HEAD.matches(method)
                || HttpMethod.OPTIONS.matches(method);
    }

    private static boolean isAllowedWrite(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        // Cerrar sesión (revoca jti) y ticket STOMP para ver el KDS.
        return path.equals("/api/v1/auth/logout")
                || path.equals("/api/v1/admin/ws-ticket");
    }
}
