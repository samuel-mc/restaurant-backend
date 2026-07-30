package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.RestaurantProfileRequest;
import com.platolisto.restaurant_backend.dto.RestaurantProfileResponse;
import com.platolisto.restaurant_backend.entity.PaymentStatus;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.plan.PlanLimits;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.storage.ObjectStorageService;
import com.platolisto.restaurant_backend.util.SafeHttpUrl;
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
    public static final int MIN_TABLE_COUNT = 1;
    public static final int MAX_TABLE_COUNT = 99;
    public static final int DEFAULT_TABLE_COUNT = 12;

    private final RestaurantRepository restaurantRepository;
    private final ObjectStorageService objectStorageService;

    @Transactional(readOnly = true)
    public RestaurantProfileResponse getProfile() {
        return mapToResponse(requireCurrentRestaurant(), true);
    }

    /** Perfil público: sin plan ni estado de cobro. */
    @Transactional(readOnly = true)
    public RestaurantProfileResponse getPublicProfile() {
        return mapToResponse(requireCurrentRestaurant(), false);
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
            restaurant.setGoogleMapsUrl(SafeHttpUrl.requireGoogleMapsUrl(request.getGoogleMapsUrl()));
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
        if (request.getOrderingEnabled() != null) {
            restaurant.setOrderingEnabled(request.getOrderingEnabled());
        }
        if (request.getTableCount() != null) {
            restaurant.setTableCount(normalizeTableCount(request.getTableCount()));
        }

        SubscriptionPlan plan = restaurant.getPlan() != null
                ? restaurant.getPlan()
                : SubscriptionPlan.BASIC;
        PaymentStatus paymentStatus = restaurant.getPaymentStatus() != null
                ? restaurant.getPaymentStatus()
                : PaymentStatus.ACTIVE;

        if (!PlanLimits.canUseProServiceModules(plan, paymentStatus)) {
            if (Boolean.TRUE.equals(request.getHasDelivery())
                    || Boolean.TRUE.equals(request.getHasPickup())
                    || Boolean.TRUE.equals(request.getHasReservations())) {
                throw new IllegalArgumentException(
                        "Para llevar, a domicilio y reservaciones están disponibles solo en Plan Pro con pago activo."
                );
            }
            restaurant.setHasDelivery(false);
            restaurant.setHasPickup(false);
            restaurant.setHasReservations(false);
        }

        if (request.getWebsitePublished() != null) {
            if (Boolean.TRUE.equals(request.getWebsitePublished())
                    && !PlanLimits.canPublishWebsite(plan, paymentStatus)) {
                throw new IllegalArgumentException(
                        "Para publicar el sitio necesitas Plan Pro con pago activo. "
                                + "Canjea un cupón en Configuración o contacta a ventas."
                );
            }
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
        return mapToResponse(saved, true);
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

    private static RestaurantProfileResponse mapToResponse(Restaurant restaurant, boolean includeBilling) {
        SubscriptionPlan plan = restaurant.getPlan() != null
                ? restaurant.getPlan()
                : SubscriptionPlan.BASIC;
        PaymentStatus paymentStatus = restaurant.getPaymentStatus() != null
                ? restaurant.getPaymentStatus()
                : PaymentStatus.ACTIVE;
        boolean proModulesAllowed = PlanLimits.canUseProServiceModules(plan, paymentStatus);

        RestaurantProfileResponse.RestaurantProfileResponseBuilder builder = RestaurantProfileResponse.builder()
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
                .hasDelivery(proModulesAllowed && restaurant.isHasDelivery())
                .hasPickup(proModulesAllowed && restaurant.isHasPickup())
                .hasReservations(proModulesAllowed && restaurant.isHasReservations())
                .orderingEnabled(restaurant.isOrderingEnabled())
                .tableCount(normalizeStoredTableCount(restaurant.getTableCount()))
                .websitePublished(restaurant.isWebsitePublished())
                .updatedAt(restaurant.getUpdatedAt());

        if (includeBilling) {
            builder.plan(plan.name()).paymentStatus(paymentStatus.name());
        }

        return builder.build();
    }

    public static int normalizeTableCount(int tableCount) {
        if (tableCount < MIN_TABLE_COUNT || tableCount > MAX_TABLE_COUNT) {
            throw new IllegalArgumentException(
                    "El total de mesas debe estar entre "
                            + MIN_TABLE_COUNT + " y " + MAX_TABLE_COUNT + "."
            );
        }
        return tableCount;
    }

    public static int normalizeStoredTableCount(int tableCount) {
        if (tableCount < MIN_TABLE_COUNT) {
            return DEFAULT_TABLE_COUNT;
        }
        if (tableCount > MAX_TABLE_COUNT) {
            return MAX_TABLE_COUNT;
        }
        return tableCount;
    }
}
