package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.CategoryRequest;
import com.platolisto.restaurant_backend.dto.CategoryResponse;
import com.platolisto.restaurant_backend.entity.Category;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.CategoryRepository;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("El restaurante asociado no existe."));

        Category category = Category.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .displayOrder(request.getDisplayOrder())
                .build();

        Category saved = categoryRepository.save(category);
        log.info("Categoría creada: ID {} en el restaurante {}", saved.getId(), restaurantId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories() {
        // Obtenemos todas las categorías ordenadas por display_order ascendentemente.
        // Debido al aspect AOP, ya vienen filtradas por restaurant_id.
        // Debido a @SQLRestriction, ya vienen filtradas deleted = false.
        List<Category> categories = categoryRepository.findAll(Sort.by(Sort.Order.asc("displayOrder")));
        
        return categories.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la categoría con ID: " + id));

        category.setName(request.getName());
        category.setDisplayOrder(request.getDisplayOrder());

        Category updated = categoryRepository.save(category);
        log.info("Categoría actualizada: ID {}", updated.getId());

        return mapToResponse(updated);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la categoría con ID: " + id));

        // Borrado lógico utilizando la anotación @SQLDelete del modelo Category
        categoryRepository.delete(category);
        log.info("Categoría eliminada lógicamente: ID {}", id);
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .displayOrder(category.getDisplayOrder())
                .createdAt(category.getCreatedAt())
                .build();
    }
}
