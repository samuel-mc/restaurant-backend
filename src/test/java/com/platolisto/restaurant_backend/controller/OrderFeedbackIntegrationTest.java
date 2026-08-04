package com.platolisto.restaurant_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platolisto.restaurant_backend.dto.SubmitFeedbackRequest;
import com.platolisto.restaurant_backend.entity.*;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.*;
import com.platolisto.restaurant_backend.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderFeedbackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderFeedbackRepository orderFeedbackRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Restaurant mockRestaurant;
    private Order closedOrder;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockRestaurant = restaurantRepository.save(Restaurant.builder()
                .name("Tacos Alerta Test")
                .subdomain("tacos-alerta-" + UUID.randomUUID().toString().substring(0, 8))
                .tableCount(10)
                .orderingEnabled(true)
                .isActive(true)
                .plan(SubscriptionPlan.PRO)
                .build());

        TenantContext.setCurrentTenant(mockRestaurant.getId());

        closedOrder = orderRepository.save(Order.builder()
                .restaurant(mockRestaurant)
                .uuid(UUID.randomUUID())
                .customerName("Juan Perez")
                .orderType(OrderType.IN_TABLE)
                .tableNumber("2")
                .status(OrderStatus.CLOSED)
                .totalAmount(new BigDecimal("250.00"))
                .build());

        User adminUser = userRepository.save(User.builder()
                .restaurant(mockRestaurant)
                .name("Manager Admin")
                .email("admin-" + UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode("Secret123"))
                .role(UserRole.ADMIN)
                .isActive(true)
                .build());

        adminToken = jwtService.generateToken(
                new org.springframework.security.core.userdetails.User(adminUser.getEmail(), adminUser.getPasswordHash(), Collections.emptyList()),
                mockRestaurant.getId(),
                "ROLE_ADMIN"
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /feedback con stars <= 2 emite alerta crítica y requiere atención de manager")
    void submitBadReview_triggersCriticalAlertAndRequiresAttention() throws Exception {
        String slug = mockRestaurant.getSubdomain();

        SubmitFeedbackRequest request = SubmitFeedbackRequest.builder()
                .stars(1)
                .comment("La atención fue pésima y la comida llegó fría.")
                .reason("SERVICE")
                .build();

        mockMvc.perform(post("/api/v1/orders/" + closedOrder.getUuid() + "/feedback")
                        .header("X-Tenant", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("PRIVATE_COMPLAINT"));

        // Verificar que la entidad guardada tenga requiresManagerAttention = true
        OrderFeedback savedFeedback = orderFeedbackRepository.findByOrderUuid(closedOrder.getUuid()).orElseThrow();
        assertTrue(savedFeedback.isRequiresManagerAttention());
        assertTrue(savedFeedback.isUrgent());

        // Verificar respuesta en el inbox del admin
        mockMvc.perform(get("/api/v1/admin/feedback")
                        .header("X-Tenant", slug)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stars", is(1)))
                .andExpect(jsonPath("$[0].requiresManagerAttention", is(true)))
                .andExpect(jsonPath("$[0].urgent", is(true)));
    }
}
