package com.platolisto.restaurant_backend.controller;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Restaurant mockRestaurant;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockRestaurant = restaurantRepository.save(Restaurant.builder()
                .name("Tacos Analytics Test")
                .subdomain("tacos-analytics-" + UUID.randomUUID().toString().substring(0, 8))
                .tableCount(10)
                .orderingEnabled(true)
                .isActive(true)
                .plan(SubscriptionPlan.PRO)
                .build());

        TenantContext.setCurrentTenant(mockRestaurant.getId());

        orderRepository.save(Order.builder()
                .restaurant(mockRestaurant)
                .uuid(UUID.randomUUID())
                .customerName("Cliente 1")
                .orderType(OrderType.IN_TABLE)
                .tableNumber("1")
                .status(OrderStatus.CLOSED)
                .paymentMethod(PaymentMethod.CASH)
                .totalAmount(new BigDecimal("300.00"))
                .build());

        User adminUser = userRepository.save(User.builder()
                .restaurant(mockRestaurant)
                .name("Manager Test")
                .email("analytics-admin-" + UUID.randomUUID() + "@test.com")
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
    @DisplayName("GET /api/v1/admin/analytics/daily-summary retorna venta acumulada y métricas del día")
    void getDailySummary_returnsMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/daily-summary")
                        .header("X-Tenant", mockRestaurant.getSubdomain())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSales", is(300.0)))
                .andExpect(jsonPath("$.totalClosedOrders", is(1)))
                .andExpect(jsonPath("$.averageTicket", is(300.0)))
                .andExpect(jsonPath("$.paymentMethods.EFECTIVO", is(300.0)))
                .andExpect(jsonPath("$.paymentMethods.TARJETA", is(0.0)))
                .andExpect(jsonPath("$.paymentMethods.TRANSFERENCIA", is(0.0)));
    }

    @Test
    @DisplayName("POST /api/v1/admin/analytics/close-shift genera Cierre de Caja / Corte Z")
    void closeShift_generatesCorteZRecord() throws Exception {
        mockMvc.perform(post("/api/v1/admin/analytics/close-shift")
                        .header("X-Tenant", mockRestaurant.getSubdomain())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.totalSales", is(300.0)))
                .andExpect(jsonPath("$.status", is("CLOSED_AUDITED")));
    }
}
