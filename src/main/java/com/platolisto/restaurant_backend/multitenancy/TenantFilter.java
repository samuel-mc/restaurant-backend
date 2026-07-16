package com.platolisto.restaurant_backend.multitenancy;

import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.exception.TenantNotFoundException;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final RestaurantRepository restaurantRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") 
                || path.startsWith("/api/v1/super-admin/")
                || path.startsWith("/api/v1/tenants/register");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // 1. Permitir consultar un tenant mediante header especial (muy útil para desarrollo local y llamadas directas de API)
            String xTenant = request.getHeader("X-Tenant");
            Restaurant restaurant = null;

            if (xTenant != null && !xTenant.isBlank()) {
                log.debug("Tenant detectado vía cabecera X-Tenant: {}", xTenant);
                restaurant = restaurantRepository.findBySubdomainAndIsActiveTrue(xTenant)
                        .orElseThrow(() -> new TenantNotFoundException("No se encontró un restaurante activo con el subdominio: " + xTenant));
            } else {
                // 2. Extraer Host de la petición HTTP
                String host = request.getHeader("Host");
                if (host == null || host.isBlank()) {
                    throw new TenantNotFoundException("El encabezado Host es requerido para identificar al restaurante.");
                }

                // Limpiar el host eliminando el puerto (e.g. "mario.platolisto.com:8080" -> "mario.platolisto.com")
                String cleanHost = host.split(":")[0].toLowerCase().trim();

                // 3. Estrategia A: Buscar por Dominio Personalizado completo
                restaurant = restaurantRepository.findByCustomDomainAndIsActiveTrue(cleanHost).orElse(null);

                if (restaurant == null) {
                    // Estrategia B: Buscar por Subdominio (e.g., 'mario.platolisto.com' -> 'mario')
                    String subdomain = extractSubdomain(cleanHost);
                    if (subdomain != null && !subdomain.isBlank()) {
                        restaurant = restaurantRepository.findBySubdomainAndIsActiveTrue(subdomain)
                                .orElseThrow(() -> new TenantNotFoundException("No se encontró un restaurante activo con el subdominio: " + subdomain));
                    }
                }

                if (restaurant == null) {
                    throw new TenantNotFoundException("No se pudo identificar ningún restaurante activo para el Host: " + cleanHost);
                }
            }

            // 4. Configurar en el ThreadLocal de la petición
            TenantContext.setCurrentTenant(restaurant.getId());

            filterChain.doFilter(request, response);

        } catch (TenantNotFoundException e) {
            log.warn("Error resolviendo tenant: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
        } finally {
            // Limpieza obligatoria del ThreadLocal al finalizar el request
            TenantContext.clear();
        }
    }

    private String extractSubdomain(String host) {
        if (host.equals("localhost") || host.equals("127.0.0.1")) {
            return null;
        }

        String[] parts = host.split("\\.");
        if (parts.length > 1) {
            if (parts[0].equalsIgnoreCase("www") && parts.length > 2) {
                return parts[1];
            }
            return parts[0];
        }
        return null;
    }
}
