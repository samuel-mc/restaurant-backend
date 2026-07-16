package com.platolisto.restaurant_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platolisto.restaurant_backend.dto.OrderDetailRequest;
import com.platolisto.restaurant_backend.dto.OrderRequest;
import com.platolisto.restaurant_backend.entity.*;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Restaurant activeRestaurant;
    private Restaurant otherRestaurant;
    private Category mockCategory;
    private Product product1;
    private Product product2;
    private Product productOtherTenant;

    @BeforeEach
    void setUp() {
        cleanup();

        // 1. Crear Restaurantes (Tenants)
        activeRestaurant = restaurantRepository.save(Restaurant.builder()
                .name("Pizza Hut")
                .subdomain("pizzahut")
                .isActive(true)
                .build());

        otherRestaurant = restaurantRepository.save(Restaurant.builder()
                .name("Burger King")
                .subdomain("burgerking")
                .isActive(true)
                .build());

        // 2. Crear Categoría en restaurante activo
        mockCategory = categoryRepository.save(Category.builder()
                .restaurant(activeRestaurant)
                .name("Pizzas")
                .displayOrder(1)
                .build());

        // 3. Crear Productos en restaurante activo
        product1 = productRepository.save(Product.builder()
                .restaurant(activeRestaurant)
                .category(mockCategory)
                .name("Pizza Pepperoni")
                .price(new BigDecimal("10.99"))
                .isAvailable(true)
                .build());

        product2 = productRepository.save(Product.builder()
                .restaurant(activeRestaurant)
                .category(mockCategory)
                .name("Refresco Grande")
                .price(new BigDecimal("2.50"))
                .isAvailable(true)
                .build());

        // 4. Crear Producto en otro restaurante
        Category otherCategory = categoryRepository.save(Category.builder()
                .restaurant(otherRestaurant)
                .name("Burgers")
                .displayOrder(1)
                .build());

        productOtherTenant = productRepository.save(Product.builder()
                .restaurant(otherRestaurant)
                .category(otherCategory)
                .name("Whopper")
                .price(new BigDecimal("5.99"))
                .isAvailable(true)
                .build());

        TenantContext.clear();
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
    void shouldCreateOrderSuccessfullyWithExactAmountAndSavedPrices() throws Exception {
        // Given: Pedido de 2 pizzas pepperoni ($10.99 c/u) y 3 refrescos ($2.50 c/u)
        // Total esperado: (2 * 10.99) + (3 * 2.50) = 21.98 + 7.50 = 29.48
        OrderDetailRequest d1 = new OrderDetailRequest(product1.getUuid(), 2, "Sin cebolla");
        OrderDetailRequest d2 = new OrderDetailRequest(product2.getUuid(), 3, "Hielo extra");

        OrderRequest request = OrderRequest.builder()
                .customerName("Juan Perez")
                .customerPhone("55512345")
                .orderType(OrderType.IN_TABLE)
                .tableNumber("5")
                .details(List.of(d1, d2))
                .build();

        // When
        String responseContent = mockMvc.perform(post("/api/v1/orders")
                        .header("X-Tenant", "pizzahut")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").exists())
                .andExpect(jsonPath("$.customerName").value("Juan Perez"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(29.48))
                .andExpect(jsonPath("$.details", hasSize(2)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID orderUuid = UUID.fromString(objectMapper.readTree(responseContent).get("uuid").asText());

        // Then: Validar directamente en base de datos física que se guardaron los precios unitarios correctos
        TenantContext.clear();
        BigDecimal pepperoniUnitPrice = jdbcTemplate.queryForObject(
                "SELECT od.unit_price FROM order_details od JOIN products p ON od.product_id = p.id WHERE od.order_id = (SELECT id FROM orders WHERE uuid = ?) AND p.uuid = ?",
                BigDecimal.class, orderUuid, product1.getUuid());

        BigDecimal sodaUnitPrice = jdbcTemplate.queryForObject(
                "SELECT od.unit_price FROM order_details od JOIN products p ON od.product_id = p.id WHERE od.order_id = (SELECT id FROM orders WHERE uuid = ?) AND p.uuid = ?",
                BigDecimal.class, orderUuid, product2.getUuid());

        assertThat(pepperoniUnitPrice).isEqualByComparingTo(new BigDecimal("10.99"));
        assertThat(sodaUnitPrice).isEqualByComparingTo(new BigDecimal("2.50"));

        // Validar directamente en base de datos física que el pedido está asignado al restaurant_id correcto
        Long assignedRestaurantId = jdbcTemplate.queryForObject(
                "SELECT restaurant_id FROM orders WHERE uuid = ?", Long.class, orderUuid);
        assertThat(assignedRestaurantId).isEqualTo(activeRestaurant.getId());
    }

    @Test
    void shouldFailOrderWhenProductIsUnavailable() throws Exception {
        // Given: Poner el refresco como no disponible
        TenantContext.setCurrentTenant(activeRestaurant.getId());
        product2.setAvailable(false);
        productRepository.save(product2);
        TenantContext.clear();

        OrderDetailRequest d1 = new OrderDetailRequest(product2.getUuid(), 1, null);
        OrderRequest request = OrderRequest.builder()
                .customerName("Maria")
                .orderType(OrderType.PICKUP)
                .details(List.of(d1))
                .build();

        // When & Then: Espera error por producto no disponible (HTTP 400 Bad Request)
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-Tenant", "pizzahut")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFailOrderWhenProductIsDeleted() throws Exception {
        // Given: Poner el refresco como borrado
        TenantContext.setCurrentTenant(activeRestaurant.getId());
        productRepository.delete(product2); // Soft delete lógico
        TenantContext.clear();

        OrderDetailRequest d1 = new OrderDetailRequest(product2.getUuid(), 1, null);
        OrderRequest request = OrderRequest.builder()
                .customerName("Carlos")
                .orderType(OrderType.PICKUP)
                .details(List.of(d1))
                .build();

        // When & Then: Espera error por producto eliminado (HTTP 400 Bad Request)
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-Tenant", "pizzahut")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFailOrderWhenProductBelongsToDifferentTenant() throws Exception {
        // Given: Pedir un producto del tenant burgerking en la api de pizzahut
        OrderDetailRequest d1 = new OrderDetailRequest(productOtherTenant.getUuid(), 1, null);
        OrderRequest request = OrderRequest.builder()
                .customerName("Ana")
                .orderType(OrderType.DELIVERY)
                .deliveryAddress("Calle Falsa 123")
                .details(List.of(d1))
                .build();

        // When & Then: Espera error por violación de aislamiento de tenant (HTTP 400 Bad Request)
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-Tenant", "pizzahut")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
