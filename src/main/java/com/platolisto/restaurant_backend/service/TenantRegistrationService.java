package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.TenantRegisterRequest;
import com.platolisto.restaurant_backend.dto.TenantRegisterResponse;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.User;
import com.platolisto.restaurant_backend.entity.UserRole;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantRegistrationService {

    private static final Set<String> RESERVED_SLUGS = Set.of(
            "www", "app", "api", "admin", "static", "assets", "mail", "ftp"
    );

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TenantRegisterResponse register(TenantRegisterRequest request) {
        String slug = request.getTenantSlug().trim().toLowerCase();
        String email = request.getOwnerEmail().trim().toLowerCase();

        if (RESERVED_SLUGS.contains(slug)) {
            throw new IllegalArgumentException("El subdominio \"" + slug + "\" está reservado. Elige otro.");
        }

        if (restaurantRepository.existsBySubdomainIgnoreCase(slug)) {
            throw new IllegalArgumentException("El subdominio \"" + slug + "\" ya está en uso. Elige otro.");
        }

        // Email globalmente único entre owners (evita confusión multi-tenant al recuperar acceso)
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Ya existe una cuenta registrada con ese correo electrónico.");
        }

        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name(request.getRestaurantName().trim())
                .subdomain(slug)
                .isActive(true)
                .websitePublished(true)
                .build());

        userRepository.save(User.builder()
                .restaurant(restaurant)
                .name(request.getOwnerName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getOwnerPassword()))
                .role(UserRole.OWNER)
                .isActive(true)
                .build());

        log.info("Tenant registrado: subdomain={}, owner={}", slug, email);

        return TenantRegisterResponse.builder()
                .restaurantId(restaurant.getId())
                .restaurantName(restaurant.getName())
                .tenantSlug(slug)
                .ownerEmail(email)
                .loginPath("/admin/login")
                .build();
    }
}
