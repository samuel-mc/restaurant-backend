package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.ProductResponse;
import com.platolisto.restaurant_backend.entity.Product;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.plan.PlanLimits;
import com.platolisto.restaurant_backend.repository.ProductRepository;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuService {

    private final ProductRepository productRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<ProductResponse> getPublicCatalog() {
        // Obtenemos solo los productos disponibles.
        // Debido a AOP y @SQLRestriction, ya se filtran por restaurant_id y deleted = false automáticamente.
        List<Product> availableProducts = productRepository.findByIsAvailableTrue();

        Long restaurantId = TenantContext.getCurrentTenant();
        SubscriptionPlan plan = SubscriptionPlan.BASIC;
        if (restaurantId != null) {
            plan = restaurantRepository.findById(restaurantId)
                    .map(Restaurant::getPlan)
                    .orElse(SubscriptionPlan.BASIC);
            if (plan == null) {
                plan = SubscriptionPlan.BASIC;
            }
        }

        List<Product> visible = PlanLimits.limitPublicCatalog(plan, availableProducts);

        return visible.stream()
                .map(product -> ProductResponse.builder()
                        .uuid(product.getUuid())
                        .name(product.getName())
                        .description(product.getDescription())
                        .price(product.getPrice())
                        .imageUrl(product.getImageUrl())
                        .isAvailable(product.isAvailable())
                        .categoryId(product.getCategory().getId())
                        .categoryName(product.getCategory().getName())
                        .createdAt(product.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
