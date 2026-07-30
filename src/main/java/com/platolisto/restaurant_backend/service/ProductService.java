package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.ProductRequest;
import com.platolisto.restaurant_backend.dto.ProductResponse;
import com.platolisto.restaurant_backend.entity.Category;
import com.platolisto.restaurant_backend.entity.Product;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.plan.PlanLimits;
import com.platolisto.restaurant_backend.repository.CategoryRepository;
import com.platolisto.restaurant_backend.repository.ProductRepository;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.storage.ObjectStorageService;
import com.platolisto.restaurant_backend.util.SafeHttpUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final RestaurantRepository restaurantRepository;
    private final ObjectStorageService objectStorageService;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        return createProduct(request, null);
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request, MultipartFile image) {
        Long restaurantId = requireRestaurantId();
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("El restaurante asociado no existe."));

        long activeCount = productRepository.countByRestaurant_Id(restaurantId);
        SubscriptionPlan plan = restaurant.getPlan() != null
                ? restaurant.getPlan()
                : SubscriptionPlan.BASIC;
        if (!PlanLimits.canCreateProduct(plan, activeCount)) {
            throw new IllegalArgumentException(PlanLimits.BASIC_PRODUCT_LIMIT_UPGRADE_MESSAGE);
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("La categoría asociada no existe."));

        String imageUrl = normalizeImageUrl(request.getImageUrl());
        if (image != null && !image.isEmpty()) {
            imageUrl = objectStorageService.uploadImage(image, restaurant.getSubdomain());
        }

        Product product = Product.builder()
                .restaurant(restaurant)
                .category(category)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .imageUrl(imageUrl)
                .isAvailable(true)
                .build();

        Product saved = productRepository.save(product);
        log.info("Producto creado: UUID {} en el restaurante {}", saved.getUuid(), restaurantId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts() {
        List<Product> products = productRepository.findAll();
        return products.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID uuid) {
        Product product = productRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el producto con UUID: " + uuid));
        return mapToResponse(product);
    }

    @Transactional
    public ProductResponse updateProduct(UUID uuid, ProductRequest request) {
        return updateProduct(uuid, request, null);
    }

    @Transactional
    public ProductResponse updateProduct(UUID uuid, ProductRequest request, MultipartFile image) {
        Long restaurantId = requireRestaurantId();
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("El restaurante asociado no existe."));

        Product product = productRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el producto con UUID: " + uuid));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("La categoría asociada no existe."));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(category);

        if (image != null && !image.isEmpty()) {
            product.setImageUrl(objectStorageService.uploadImage(image, restaurant.getSubdomain()));
        } else if (request.getImageUrl() != null) {
            product.setImageUrl(normalizeImageUrl(request.getImageUrl()));
        }

        Product updated = productRepository.save(product);
        log.info("Producto actualizado: UUID {}", updated.getUuid());

        return mapToResponse(updated);
    }

    @Transactional
    public void deleteProduct(UUID uuid) {
        Product product = productRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el producto con UUID: " + uuid));

        productRepository.delete(product);
        log.info("Producto eliminado lógicamente: UUID {}", uuid);
    }

    @Transactional
    public ProductResponse toggleAvailability(UUID uuid) {
        Product product = productRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el producto con UUID: " + uuid));

        product.setAvailable(!product.isAvailable());
        Product updated = productRepository.save(product);
        log.info("Disponibilidad del producto cambiada a {}: UUID {}", updated.isAvailable(), uuid);

        return mapToResponse(updated);
    }

    private Long requireRestaurantId() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }
        return restaurantId;
    }

    private String normalizeImageUrl(String raw) {
        return SafeHttpUrl.requireHttpsOrHttp(raw, "La URL de imagen");
    }

    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .uuid(product.getUuid())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .imageUrl(product.getImageUrl())
                .isAvailable(product.isAvailable())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .createdAt(product.getCreatedAt())
                .modifierGroups(new java.util.ArrayList<>(ProductModifierService.mapGroups(product)))
                .build();
    }
}
