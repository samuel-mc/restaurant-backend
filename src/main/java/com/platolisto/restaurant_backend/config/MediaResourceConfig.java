package com.platolisto.restaurant_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class MediaResourceConfig implements WebMvcConfigurer {

    private final String localRoot;

    public MediaResourceConfig(
            @Value("${application.storage.local-root:uploads}") String localRoot
    ) {
        this.localRoot = localRoot;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(localRoot).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/media/**")
                .addResourceLocations(location);
    }
}
