package com.platolisto.restaurant_backend.multitenancy;

import com.platolisto.restaurant_backend.entity.Category;
import com.platolisto.restaurant_backend.entity.Product;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.repository.CategoryRepository;
import com.platolisto.restaurant_backend.repository.ProductRepository;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TenantDataIsolationTest {

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Restaurant tenant1;
    private Restaurant tenant2;

    @BeforeEach
    void setUp() {
        // Limpiar base de datos antes del test
        cleanup();

        // 1. Crear Restaurantes (Tenants)
        tenant1 = restaurantRepository.save(Restaurant.builder()
                .name("Burgers & Fries")
                .subdomain("burgers")
                .isActive(true)
                .build());

        tenant2 = restaurantRepository.save(Restaurant.builder()
                .name("Pizza Palace")
                .subdomain("pizza")
                .isActive(true)
                .build());

        // Asegurarse de limpiar el TenantContext para la inserción
        TenantContext.clear();

        // 2. Crear Categorías
        Category catTenant1 = categoryRepository.save(Category.builder()
                .restaurant(tenant1)
                .name("Combos")
                .build());

        Category catTenant2 = categoryRepository.save(Category.builder()
                .restaurant(tenant2)
                .name("Pizzas")
                .build());

        // 3. Crear Productos
        productRepository.save(Product.builder()
                .restaurant(tenant1)
                .category(catTenant1)
                .name("Mega Burger Combo")
                .price(new BigDecimal("12.50"))
                .isAvailable(true)
                .build());

        productRepository.save(Product.builder()
                .restaurant(tenant1)
                .category(catTenant1)
                .name("Veggie Burger Combo")
                .price(new BigDecimal("11.00"))
                .isAvailable(true)
                .build());

        productRepository.save(Product.builder()
                .restaurant(tenant2)
                .category(catTenant2)
                .name("Pepperoni Pizza Large")
                .price(new BigDecimal("18.99"))
                .isAvailable(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        cleanup();
    }

    private void cleanup() {
        jdbcTemplate.execute("DELETE FROM order_details");
        jdbcTemplate.execute("DELETE FROM orders");
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    void shouldOnlyReturnProductsForTenant1WhenTenant1IsActive() {
        // Given: Configurar el ID del Tenant 1 en el contexto
        TenantContext.setCurrentTenant(tenant1.getId());

        // When: Consultar todos los productos
        List<Product> products = productRepository.findAll();

        // Then: Debe retornar únicamente los 2 productos asociados a Tenant 1
        assertThat(products).hasSize(2);
        assertThat(products).extracting(Product::getName)
                .containsExactlyInAnyOrder("Mega Burger Combo", "Veggie Burger Combo");
    }

    @Test
    void shouldOnlyReturnProductsForTenant2WhenTenant2IsActive() {
        // Given: Configurar el ID del Tenant 2 en el contexto
        TenantContext.setCurrentTenant(tenant2.getId());

        // When: Consultar todos los productos
        List<Product> products = productRepository.findAll();

        // Then: Debe retornar únicamente el producto asociado a Tenant 2
        assertThat(products).hasSize(1);
        assertThat(products).extracting(Product::getName)
                .containsExactly("Pepperoni Pizza Large");
    }

    @Test
    void shouldReturnAllProductsWhenTenantContextIsEmpty() {
        // Given: TenantContext vacío (por ejemplo, Super Admin o Batch Job)
        TenantContext.clear();

        // When: Consultar todos los productos
        List<Product> products = productRepository.findAll();

        // Then: Retorna todos los productos existentes en la base de datos sin filtrar
        assertThat(products).hasSize(3);
        assertThat(products).extracting(Product::getName)
                .containsExactlyInAnyOrder("Mega Burger Combo", "Veggie Burger Combo", "Pepperoni Pizza Large");
    }
}
