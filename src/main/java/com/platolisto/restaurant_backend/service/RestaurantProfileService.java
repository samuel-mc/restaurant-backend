package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.RestaurantProfileRequest;
import com.platolisto.restaurant_backend.dto.RestaurantProfileResponse;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantProfileService {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final RestaurantRepository restaurantRepository;
    private final ObjectStorageService objectStorageService;

    @Transactional(readOnly = true)
    public RestaurantProfileResponse getProfile() {
        return mapToResponse(requireCurrentRestaurant());
    }

    @Transactional
    public RestaurantProfileResponse updateProfile(
            RestaurantProfileRequest request,
            MultipartFile logo,
            MultipartFile banner
    ) {
        Restaurant restaurant = requireCurrentRestaurant();

        if (request.getName() != null && !request.getName().isBlank()) {
            restaurant.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            restaurant.setDescription(blankToNull(request.getDescription()));
        }
        if (request.getPrimaryColor() != null) {
            restaurant.setPrimaryColor(normalizeColor(request.getPrimaryColor(), "primaryColor"));
        }
        if (request.getSecondaryColor() != null) {
            restaurant.setSecondaryColor(normalizeColor(request.getSecondaryColor(), "secondaryColor"));
        }
        if (request.getAddress() != null) {
            restaurant.setAddress(blankToNull(request.getAddress()));
        }
        if (request.getGoogleMapsUrl() != null) {
            restaurant.setGoogleMapsUrl(blankToNull(request.getGoogleMapsUrl()));
        }
        if (request.getWhatsapp() != null) {
            restaurant.setWhatsapp(blankToNull(request.getWhatsapp()));
        }
        if (request.getBusinessHours() != null) {
            restaurant.setBusinessHours(blankToNull(request.getBusinessHours()));
        }
        if (request.getHasDelivery() != null) {
            restaurant.setHasDelivery(request.getHasDelivery());
        }
        if (request.getHasPickup() != null) {
            restaurant.setHasPickup(request.getHasPickup());
        }
        if (request.getHasReservations() != null) {
            restaurant.setHasReservations(request.getHasReservations());
        }
        if (request.getWebsitePublished() != null) {
            restaurant.setWebsitePublished(request.getWebsitePublished());
        }

        String slug = restaurant.getSubdomain();
        if (logo != null && !logo.isEmpty()) {
            restaurant.setLogoUrl(objectStorageService.uploadBrandAsset(logo, slug, "logo"));
        }
        if (banner != null && !banner.isEmpty()) {
            restaurant.setBannerUrl(objectStorageService.uploadBrandAsset(banner, slug, "banner"));
        }

        Restaurant saved = restaurantRepository.save(restaurant);
        log.info("Perfil del restaurante actualizado: {}", saved.getSubdomain());
        return mapToResponse(saved);
    }

    private Restaurant requireCurrentRestaurant() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("El restaurante asociado no existe."));
    }

    private static String normalizeColor(String raw, String field) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!value.startsWith("#") && value.length() == 6) {
            value = "#" + value;
        }
        if (!HEX_COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "El campo " + field + " debe ser un color HEX válido (#RRGGBB)."
            );
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static RestaurantProfileResponse mapToResponse(Restaurant restaurant) {
        return RestaurantProfileResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .subdomain(restaurant.getSubdomain())
                .logoUrl(restaurant.getLogoUrl())
                .bannerUrl(restaurant.getBannerUrl())
                .primaryColor(restaurant.getPrimaryColor())
                .secondaryColor(restaurant.getSecondaryColor())
                .description(restaurant.getDescription())
                .address(restaurant.getAddress())
                .googleMapsUrl(restaurant.getGoogleMapsUrl())
                .whatsapp(restaurant.getWhatsapp())
                .businessHours(restaurant.getBusinessHours())
                .hasDelivery(restaurant.isHasDelivery())
                .hasPickup(restaurant.isHasPickup())
                .hasReservations(restaurant.isHasReservations())
                .websitePublished(restaurant.isWebsitePublished())
                .updatedAt(restaurant.getUpdatedAt())
                .build();
    }
}
