package com.platolisto.restaurant_backend.config;

import com.platolisto.restaurant_backend.multitenancy.TenantFilter;
import com.platolisto.restaurant_backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TenantFilter tenantFilter;
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Value("${application.security.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // Rutas públicas de Autenticación y Menú del Comensal
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/staff/login-pin").permitAll()
                .requestMatchers("/api/v1/public/staff", "/api/v1/public/staff/**").permitAll()
                .requestMatchers("/api/v1/superadmin/auth/login").permitAll()
                .requestMatchers("/api/v1/tenants/register").permitAll()
                .requestMatchers("/api/v1/menu/**").permitAll()
                .requestMatchers("/api/v1/orders/**").permitAll()
                .requestMatchers("/api/v1/restaurants/profile").permitAll()
                // Handshake WS público; auth real en STOMP CONNECT/SUBSCRIBE
                .requestMatchers("/ws-orders", "/ws-orders/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/error").permitAll()
                // Imágenes de productos (local hoy; R2 CDN mañana)
                .requestMatchers("/media/**").permitAll()

                // Backoffice global de la plataforma
                .requestMatchers("/api/v1/superadmin/**").hasRole("SUPER_ADMIN")

                // Cocina (KDS) — COCINA + ADMIN (+ OWNER email legacy)
                .requestMatchers("/api/v1/admin/kitchen/**")
                    .hasAnyRole("COCINA", "ADMIN", "OWNER")

                // Pedidos / comandas — MESERO opera mesas; COCINA también usa esta API para el KDS
                .requestMatchers("/api/v1/admin/orders/**")
                    .hasAnyRole("MESERO", "COCINA", "ADMIN", "OWNER")

                // Equipo, facturación, menú, settings, analytics — solo administración
                .requestMatchers(
                        "/api/v1/admin/team/**",
                        "/api/v1/admin/billing/**",
                        "/api/v1/admin/categories/**",
                        "/api/v1/admin/products/**",
                        "/api/v1/admin/menu/**",
                        "/api/v1/admin/restaurants/**",
                        "/api/v1/admin/analytics/**"
                ).hasAnyRole("ADMIN", "OWNER")

                // Cualquier otra ruta admin: solo ADMIN/OWNER
                .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "OWNER")

                // Cualquier otra ruta requiere autenticación genérica
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(tenantFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type", "X-Tenant"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
