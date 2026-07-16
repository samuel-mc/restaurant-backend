package com.platolisto.restaurant_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platolisto.restaurant_backend.dto.OrderDetailRequest;
import com.platolisto.restaurant_backend.dto.OrderRequest;
import com.platolisto.restaurant_backend.dto.OrderStatusRequest;
import com.platolisto.restaurant_backend.dto.OrderResponse;
import com.platolisto.restaurant_backend.entity.*;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.*;
import com.platolisto.restaurant_backend.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminOrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Restaurant mockRestaurant;
    private User mockAdmin;
    private String jwtToken;
    private Product mockProduct;

    @BeforeEach
    void setUp() {
        cleanup();

        // 1. Crear Restaurante
        mockRestaurant = restaurantRepository.save(Restaurant.builder()
                .name("Burgers")
                .subdomain("burgers")
                .isActive(true)
                .build());

        // 2. Crear Administrador
        mockAdmin = userRepository.save(User.builder()
                .restaurant(mockRestaurant)
                .name("Pedro Gomez")
                .email("pedro@burgers.com")
                .passwordHash(passwordEncoder.encode("Secret123"))
                .role(UserRole.ADMIN)
                .isActive(true)
                .build());

        // 3. Crear Categoría y Producto
        Category category = categoryRepository.save(Category.builder()
                .restaurant(mockRestaurant)
                .name("Hamburguesas")
                .displayOrder(1)
                .build());

        mockProduct = productRepository.save(Product.builder()
                .restaurant(mockRestaurant)
                .category(category)
                .name("Clásica")
                .price(new BigDecimal("5.00"))
                .isAvailable(true)
                .build());

        // 4. Generar Token JWT
        org.springframework.security.core.userdetails.User springUser = 
                new org.springframework.security.core.userdetails.User(
                        mockAdmin.getEmail(), 
                        mockAdmin.getPasswordHash(), 
                        Collections.emptyList()
                );
        jwtToken = jwtService.generateToken(springUser, mockRestaurant.getId(), mockAdmin.getRole().name());

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        cleanup();
    }

    private void cleanup() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    void shouldCreateOrderAndTriggerWebSocketNotification() throws Exception {
        // Given
        OrderDetailRequest d1 = new OrderDetailRequest(mockProduct.getUuid(), 2, null);
        OrderRequest request = OrderRequest.builder()
                .customerName("Ana Lopez")
                .orderType(OrderType.IN_TABLE)
                .tableNumber("3")
                .details(List.of(d1))
                .build();

        // When: Crear pedido (POST público)
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-Tenant", "burgers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").exists());

        // Then: Verificar que se envió la notificación WebSocket al restaurante burgers
        String expectedTopic = "/topic/restaurants/" + mockRestaurant.getId() + "/orders";
        verify(messagingTemplate, times(1)).convertAndSend(eq(expectedTopic), any(OrderResponse.class));
    }

    @Test
    void shouldUpdateOrderStatusAndTriggerWebSocketNotification() throws Exception {
        // Given: Primero crear un pedido
        OrderDetailRequest d1 = new OrderDetailRequest(mockProduct.getUuid(), 1, null);
        OrderRequest request = OrderRequest.builder()
                .customerName("Ana Lopez")
                .orderType(OrderType.IN_TABLE)
                .tableNumber("3")
                .details(List.of(d1))
                .build();

        String responseContent = mockMvc.perform(post("/api/v1/orders")
                        .header("X-Tenant", "burgers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID orderUuid = UUID.fromString(objectMapper.readTree(responseContent).get("uuid").asText());

        // Resetear mock de template para ignorar la llamada de creación y enfocarnos en la actualización
        Mockito.reset(messagingTemplate);

        // When: Actualizar estado a ACCEPTED (PATCH protegido requiriendo JWT de ADMIN)
        OrderStatusRequest statusRequest = new OrderStatusRequest(OrderStatus.ACCEPTED);

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderUuid + "/status")
                        .header("X-Tenant", "burgers")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Then: Verificar que se envió la notificación WebSocket de la actualización de estado
        String expectedTopic = "/topic/restaurants/" + mockRestaurant.getId() + "/orders";
        verify(messagingTemplate, times(1)).convertAndSend(eq(expectedTopic), any(OrderResponse.class));

        // Validar directamente en base de datos el cambio de estado
        TenantContext.clear();
        Order orderInDb = orderRepository.findByUuid(orderUuid).orElseThrow();
        assertThat(orderInDb.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void shouldDenyStatusUpdateWithoutAuthorizationToken() throws Exception {
        // Given
        UUID randomUuid = UUID.randomUUID();
        OrderStatusRequest statusRequest = new OrderStatusRequest(OrderStatus.ACCEPTED);

        // When & Then: Petición anónima a ruta protegida de administración (espera 403 Forbidden o 401)
        mockMvc.perform(patch("/api/v1/admin/orders/" + randomUuid + "/status")
                        .header("X-Tenant", "burgers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isForbidden());
    }
}
