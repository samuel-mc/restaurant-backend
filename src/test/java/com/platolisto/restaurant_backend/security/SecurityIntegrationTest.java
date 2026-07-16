package com.platolisto.restaurant_backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platolisto.restaurant_backend.dto.LoginRequest;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.User;
import com.platolisto.restaurant_backend.entity.UserRole;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.context.annotation.Import;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityIntegrationTest.AdminTestController.class)
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Restaurant mockRestaurant;
    private User mockUser;

    @BeforeEach
    void setUp() {
        cleanup();

        // 1. Crear Tenant
        mockRestaurant = restaurantRepository.save(Restaurant.builder()
                .name("Tacos El Pastor")
                .subdomain("tacos")
                .isActive(true)
                .build());

        // 2. Crear Administrador en el Tenant
        mockUser = userRepository.save(User.builder()
                .restaurant(mockRestaurant)
                .name("Mario Rossi")
                .email("mario@rossi.com")
                .passwordHash(passwordEncoder.encode("Secret123"))
                .role(UserRole.ADMIN)
                .isActive(true)
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
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    void shouldLoginSuccessfullyAndReturnJwt() throws Exception {
        // Given
        LoginRequest loginRequest = new LoginRequest("mario@rossi.com", "Secret123");

        // When & Then
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", "tacos") // Interceptor de tenant requerido
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void shouldFailLoginWhenPasswordIsIncorrect() throws Exception {
        // Given
        LoginRequest loginRequest = new LoginRequest("mario@rossi.com", "WrongPassword");

        // When & Then (Spring Security lanza BadCredentialsException, que resuelve a 401 Unauthorized)
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", "tacos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401Or403WhenAccessingProtectedWithoutToken() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/admin/test")
                        .header("X-Tenant", "tacos"))
                .andExpect(status().isForbidden()); // Spring Security devuelve Forbidden por defecto para anonymous sin acceso
    }

    @Test
    void shouldAllowAccessWhenTokenIsValidAndTenantMatches() throws Exception {
        // Given: Generar token válido de antemano
        org.springframework.security.core.userdetails.User springUser = 
                new org.springframework.security.core.userdetails.User(
                        mockUser.getEmail(), 
                        mockUser.getPasswordHash(), 
                        java.util.Collections.emptyList()
                );
        String jwtToken = jwtService.generateToken(springUser, mockRestaurant.getId(), mockUser.getRole().name());

        // When & Then
        mockMvc.perform(get("/api/v1/admin/test")
                        .header("X-Tenant", "tacos") // Mismo tenant que el del token
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(status().is(200));
    }

    @Test
    void shouldDenyAccessWhenTokenBelongsToDifferentTenant() throws Exception {
        // Given: Generar token para restaurantId 999L (divergente del tenant activo "tacos")
        org.springframework.security.core.userdetails.User springUser = 
                new org.springframework.security.core.userdetails.User(
                        mockUser.getEmail(), 
                        mockUser.getPasswordHash(), 
                        java.util.Collections.emptyList()
                );
        String jwtToken = jwtService.generateToken(springUser, 999L, mockUser.getRole().name());

        // When & Then
        mockMvc.perform(get("/api/v1/admin/test")
                        .header("X-Tenant", "tacos") // Tenant activo = tacos (id de mockRestaurant)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isForbidden()); // Validado por JwtAuthenticationFilter (Cross-tenant check)
    }

    // Controlador Dummy de prueba en la ruta protegida /api/v1/admin/**
    @RestController
    @RequestMapping("/api/v1/admin/test")
    static class AdminTestController {
        @GetMapping
        public String test() {
            return "SUCCESS";
        }
    }
}
