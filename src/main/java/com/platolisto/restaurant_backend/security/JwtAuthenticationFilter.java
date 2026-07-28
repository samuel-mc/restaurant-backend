package com.platolisto.restaurant_backend.security;

import com.platolisto.restaurant_backend.entity.StaffMember;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.StaffMemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final StaffMemberRepository staffMemberRepository;

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
        final String subject;
        try {
            subject = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            log.warn("No se pudo extraer el subject/username del token JWT: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        if (subject != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails;
            try {
                if (jwtService.isStaffToken(jwt) || StaffUserDetails.isStaffSubject(subject)) {
                    userDetails = loadStaffDetails(jwt, subject);
                } else {
                    userDetails = this.userDetailsService.loadUserByUsername(subject);
                }
            } catch (Exception e) {
                log.warn("No se pudo cargar el principal del JWT: {}", e.getMessage());
                filterChain.doFilter(request, response);
                return;
            }

            if (userDetails != null && jwtService.isTokenValid(jwt, userDetails) && userDetails.isEnabled()) {
                Long activeTenantId = TenantContext.getCurrentTenant();
                Long tokenTenantId = jwtService.extractRestaurantId(jwt);
                String role = jwtService.extractRole(jwt);

                if (activeTenantId != null && !"SUPER_ADMIN".equals(role)) {
                    if (tokenTenantId == null || !tokenTenantId.equals(activeTenantId)) {
                        log.warn(
                                "Intento de acceso cruzado denegado: Token pertenece al restaurant_id {} pero la petición es para el restaurant_id {}. Subject: {}",
                                tokenTenantId,
                                activeTenantId,
                                subject
                        );
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\": \"Acceso no autorizado a este restaurante.\"}");
                        return;
                    }
                }

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }

    private UserDetails loadStaffDetails(String jwt, String subject) {
        UUID staffId = StaffUserDetails.parseStaffId(subject);
        if (staffId == null) {
            String claimId = jwtService.extractStaffId(jwt);
            if (claimId != null) {
                staffId = UUID.fromString(claimId);
            }
        }
        if (staffId == null) {
            throw new IllegalArgumentException("Token de staff sin staffId");
        }

        Long restaurantId = jwtService.extractRestaurantId(jwt);
        if (restaurantId == null) {
            throw new IllegalArgumentException("Token de staff sin restaurantId");
        }

        StaffMember member = staffMemberRepository.findByIdAndRestaurantId(staffId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Staff no encontrado"));

        // No tocar la asociación lazy restaurant: el filtro corre fuera de OSIV.
        return StaffUserDetails.fromMember(member, restaurantId);
    }
}
