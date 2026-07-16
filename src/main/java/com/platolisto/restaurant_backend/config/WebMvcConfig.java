package com.platolisto.restaurant_backend.config;

import com.platolisto.restaurant_backend.multitenancy.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Registrar el interceptor para todas las rutas del API pública y privada de restaurantes
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**")
                // Se pueden excluir rutas globales de registro de tenants o del super administrador
                .excludePathPatterns(
                        "/api/v1/super-admin/**",
                        "/api/v1/tenants/register",
                        "/static/**",
                        "/error"
                );
    }
}
