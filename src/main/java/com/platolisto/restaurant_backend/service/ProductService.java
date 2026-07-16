package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.ProductRequest;
import com.platolisto.restaurant_backend.dto.ProductResponse;
import com.platolisto.restaurant_backend.entity.Category;
import com.platolisto.restaurant_backend.entity.Product;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.CategoryRepository;
import com.platolisto.restaurant_backend.repository.ProductRepository;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("El restaurante asociado no existe."));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("La categoría asociada no existe."));

        Product product = Product.builder()
                .restaurant(restaurant)
                .category(category)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .imageUrl(request.getImageUrl())
                .isAvailable(true)
                .build();

        Product saved = productRepository.save(product);
        log.info("Producto creado: UUID {} en el restaurante {}", saved.getUuid(), restaurantId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts() {
        // Obtenemos los productos activos. Debido al AOP ya están filtrados por restaurant y deleted = false.
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
        Product product = productRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el producto con UUID: " + uuid));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("La categoría asociada no existe."));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setImageUrl(request.getImageUrl());
        product.setCategory(category);

        Product updated = productRepository.save(product);
        log.info("Producto actualizado: UUID {}", updated.getUuid());

        return mapToResponse(updated);
    }

    @Transactional
    public void deleteProduct(UUID uuid) {
        Product product = productRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el producto con UUID: " + uuid));

        // Soft Delete
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
                .build();
    }
}
