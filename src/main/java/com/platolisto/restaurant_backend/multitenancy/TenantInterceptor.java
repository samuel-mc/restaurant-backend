package com.platolisto.restaurant_backend.multitenancy;

import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.exception.TenantNotFoundException;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

    private final RestaurantRepository restaurantRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. Permitir consultar un tenant mediante header especial (muy útil para desarrollo local y llamadas directas de API)
        String xTenant = request.getHeader("X-Tenant");
        if (xTenant != null && !xTenant.isBlank()) {
            log.debug("Tenant detectado vía cabecera X-Tenant: {}", xTenant);
            Restaurant restaurant = restaurantRepository.findBySubdomainAndIsActiveTrue(xTenant)
                    .orElseThrow(() -> new TenantNotFoundException("No se encontró un restaurante activo con el subdominio: " + xTenant));
            TenantContext.setCurrentTenant(restaurant.getId());
            return true;
        }

        // 2. Extraer Host de la petición HTTP
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            throw new TenantNotFoundException("El encabezado Host es requerido para identificar al restaurante.");
        }

        // Limpiar el host eliminando el puerto (e.g. "mario.platolisto.com:8080" -> "mario.platolisto.com")
        String cleanHost = host.split(":")[0].toLowerCase().trim();

        // 3. Estrategia A: Buscar por Dominio Personalizado completo (e.g., 'pizzeriamario.com')
        Restaurant restaurant = restaurantRepository.findByCustomDomainAndIsActiveTrue(cleanHost).orElse(null);

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

        // 4. Configurar en el ThreadLocal de la petición
        TenantContext.setCurrentTenant(restaurant.getId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // Limpieza obligatoria del ThreadLocal para prevenir memory leaks
        TenantContext.clear();
    }

    /**
     * Extrae el subdominio del host.
     * Ejemplo: "mario.platolisto.com" -> "mario"
     * "mario.localhost" -> "mario"
     */
    private String extractSubdomain(String host) {
        if (host.equals("localhost") || host.equals("127.0.0.1")) {
            return null;
        }

        String[] parts = host.split("\\.");
        if (parts.length > 1) {
            // Retorna la primera sección del host como el subdominio
            // e.g. "mario.platolisto.com" -> parts[0] = "mario"
            // Se puede omitir "www" si es necesario
            if (parts[0].equalsIgnoreCase("www") && parts.length > 2) {
                return parts[1];
            }
            return parts[0];
        }
        return null;
    }
}
