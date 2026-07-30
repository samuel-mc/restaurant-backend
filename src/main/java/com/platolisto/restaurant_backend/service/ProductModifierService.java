package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.ProductModifierGroupRequest;
import com.platolisto.restaurant_backend.dto.ProductModifierGroupResponse;
import com.platolisto.restaurant_backend.dto.ProductModifierOptionRequest;
import com.platolisto.restaurant_backend.dto.ProductModifierOptionResponse;
import com.platolisto.restaurant_backend.dto.ProductResponse;
import com.platolisto.restaurant_backend.dto.ReplaceProductModifiersRequest;
import com.platolisto.restaurant_backend.entity.Product;
import com.platolisto.restaurant_backend.entity.ProductModifier;
import com.platolisto.restaurant_backend.entity.ProductModifierGroup;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductModifierService {

    private final ProductRepository productRepository;
    private final ProductService productService;

    @Transactional
    public ProductResponse replaceGroups(UUID productUuid, ReplaceProductModifiersRequest request) {
        Long restaurantId = requireTenant();
        Product product = productRepository.findByUuid(productUuid)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado."));
        if (!product.getRestaurant().getId().equals(restaurantId)) {
            throw new IllegalArgumentException("Producto no encontrado.");
        }

        // Soft-delete vía orphanRemoval + @SQLDelete al limpiar la colección.
        product.getModifierGroups().clear();

        List<ProductModifierGroupRequest> groups = request.getGroups() == null
                ? List.of()
                : request.getGroups();

        int groupIndex = 0;
        for (ProductModifierGroupRequest groupReq : groups) {
            validateGroup(groupReq);
            ProductModifierGroup group = ProductModifierGroup.builder()
                    .uuid(UUID.randomUUID())
                    .restaurant(product.getRestaurant())
                    .product(product)
                    .name(groupReq.getName().trim())
                    .minSelect(groupReq.getMinSelect())
                    .maxSelect(groupReq.getMaxSelect())
                    .displayOrder(groupReq.getDisplayOrder() > 0 ? groupReq.getDisplayOrder() : groupIndex)
                    .build();

            int optionIndex = 0;
            for (ProductModifierOptionRequest optReq : groupReq.getOptions()) {
                ProductModifier option = ProductModifier.builder()
                        .uuid(UUID.randomUUID())
                        .restaurant(product.getRestaurant())
                        .group(group)
                        .name(optReq.getName().trim())
                        .priceDelta(normalizeMoney(optReq.getPriceDelta()))
                        .isAvailable(optReq.isAvailable())
                        .displayOrder(optReq.getDisplayOrder() > 0 ? optReq.getDisplayOrder() : optionIndex)
                        .build();
                group.addOption(option);
                optionIndex++;
            }
            product.addModifierGroup(group);
            groupIndex++;
        }

        productRepository.save(product);
        return productService.getProduct(productUuid);
    }

    static List<ProductModifierGroupResponse> mapGroups(Product product) {
        if (product.getModifierGroups() == null || product.getModifierGroups().isEmpty()) {
            return List.of();
        }
        return product.getModifierGroups().stream()
                .sorted(Comparator
                        .comparingInt(ProductModifierGroup::getDisplayOrder)
                        .thenComparing(ProductModifierGroup::getId, Comparator.nullsLast(Long::compareTo)))
                .map(ProductModifierService::mapGroup)
                .toList();
    }

    static ProductModifierGroupResponse mapGroup(ProductModifierGroup group) {
        List<ProductModifierOptionResponse> options = group.getOptions() == null
                ? List.of()
                : group.getOptions().stream()
                .sorted(Comparator
                        .comparingInt(ProductModifier::getDisplayOrder)
                        .thenComparing(ProductModifier::getId, Comparator.nullsLast(Long::compareTo)))
                .map(opt -> ProductModifierOptionResponse.builder()
                        .uuid(opt.getUuid())
                        .name(opt.getName())
                        .priceDelta(opt.getPriceDelta())
                        .available(opt.isAvailable())
                        .displayOrder(opt.getDisplayOrder())
                        .build())
                .toList();

        return ProductModifierGroupResponse.builder()
                .uuid(group.getUuid())
                .name(group.getName())
                .minSelect(group.getMinSelect())
                .maxSelect(group.getMaxSelect())
                .displayOrder(group.getDisplayOrder())
                .options(new ArrayList<>(options))
                .build();
    }

    private static void validateGroup(ProductModifierGroupRequest groupReq) {
        if (groupReq.getMaxSelect() < groupReq.getMinSelect()) {
            throw new IllegalArgumentException(
                    "En \"" + groupReq.getName() + "\", el máximo de opciones debe ser ≥ al mínimo."
            );
        }
        if (groupReq.getOptions() == null || groupReq.getOptions().isEmpty()) {
            throw new IllegalArgumentException(
                    "El grupo \"" + groupReq.getName() + "\" necesita al menos una opción."
            );
        }
        if (groupReq.getMaxSelect() > groupReq.getOptions().size()) {
            throw new IllegalArgumentException(
                    "En \"" + groupReq.getName() + "\", el máximo no puede superar el número de opciones."
            );
        }
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static Long requireTenant() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }
        return restaurantId;
    }
}
