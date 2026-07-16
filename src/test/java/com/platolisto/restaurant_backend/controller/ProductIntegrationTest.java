package com.platolisto.restaurant_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platolisto.restaurant_backend.dto.ProductRequest;
import com.platolisto.restaurant_backend.dto.ProductResponse;
import com.platolisto.restaurant_backend.entity.Category;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.User;
import com.platolisto.restaurant_backend.entity.UserRole;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.CategoryRepository;
import com.platolisto.restaurant_backend.repository.ProductRepository;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.repository.UserRepository;
import com.platolisto.restaurant_backend.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Restaurant mockRestaurant;
    private Category mockCategory;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        cleanup();

        // 1. Crear Restaurante
        mockRestaurant = restaurantRepository.save(Restaurant.builder()
                .name("KFC")
                .subdomain("kfc")
                .isActive(true)
                .build());

        // 2. Crear Administrador
        User mockUser = userRepository.save(User.builder()
                .restaurant(mockRestaurant)
                .name("KFC Admin")
                .email("admin@kfc.com")
                .passwordHash(passwordEncoder.encode("Secret123"))
                .role(UserRole.ADMIN)
                .isActive(true)
                .build());

        // 3. Crear Categoría
        mockCategory = categoryRepository.save(Category.builder()
                .restaurant(mockRestaurant)
                .name("Pollos")
                .displayOrder(1)
                .build());

        // 4. Generar Token JWT
        org.springframework.security.core.userdetails.User springUser = 
                new org.springframework.security.core.userdetails.User(
                        mockUser.getEmail(), 
                        mockUser.getPasswordHash(), 
                        Collections.emptyList()
                );
        jwtToken = jwtService.generateToken(springUser, mockRestaurant.getId(), mockUser.getRole().name());

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
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    void shouldCreateProductSuccessfully() throws Exception {
        // Given
        ProductRequest request = ProductRequest.builder()
                .name("Familiar Combo 12 Pzas")
                .description("12 piezas de pollo frito con papas")
                .price(new BigDecimal("22.50"))
                .categoryId(mockCategory.getId())
                .build();

        // When
        String responseContent = mockMvc.perform(post("/api/v1/admin/products")
                        .header("X-Tenant", "kfc")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").exists())
                .andExpect(jsonPath("$.name").value("Familiar Combo 12 Pzas"))
                .andExpect(jsonPath("$.price").value(22.50))
                .andExpect(jsonPath("$.categoryName").value("Pollos"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID productUuid = UUID.fromString(objectMapper.readTree(responseContent).get("uuid").asText());

        // Then: Validar en BD física que tenga el restaurant_id correcto
        TenantContext.clear();
        Long assignedRestaurantId = jdbcTemplate.queryForObject(
                "SELECT restaurant_id FROM products WHERE uuid = ?", Long.class, productUuid);

        assertThat(assignedRestaurantId).isEqualTo(mockRestaurant.getId());
    }

    @Test
    void shouldToggleAvailabilitySuccessfully() throws Exception {
        // Given: Crear un producto
        ProductRequest request = ProductRequest.builder()
                .name("Pieza de Pollo")
                .price(new BigDecimal("2.50"))
                .categoryId(mockCategory.getId())
                .build();

        String responseContent = createProductRequest(request);
        UUID productUuid = UUID.fromString(objectMapper.readTree(responseContent).get("uuid").asText());

        // When: Cambiar disponibilidad (Toggle)
        mockMvc.perform(patch("/api/v1/admin/products/" + productUuid + "/toggle-availability")
                        .header("X-Tenant", "kfc")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false)); // Por defecto era true, ahora es false

        // When: Cambiar de nuevo (Toggle back)
        mockMvc.perform(patch("/api/v1/admin/products/" + productUuid + "/toggle-availability")
                        .header("X-Tenant", "kfc")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void shouldSoftDeleteProductSuccessfully() throws Exception {
        // Given: Crear un producto
        ProductRequest request = ProductRequest.builder()
                .name("Puré Familiar")
                .price(new BigDecimal("4.00"))
                .categoryId(mockCategory.getId())
                .build();

        String responseContent = createProductRequest(request);
        UUID productUuid = UUID.fromString(objectMapper.readTree(responseContent).get("uuid").asText());

        // When: Borrar producto
        mockMvc.perform(delete("/api/v1/admin/products/" + productUuid)
                        .header("X-Tenant", "kfc")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNoContent());

        // Then: Listar productos y verificar que ya no se retorna
        mockMvc.perform(get("/api/v1/admin/products")
                        .header("X-Tenant", "kfc")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Then: Verificar en la BD que físicamente existe con deleted = true
        TenantContext.clear();
        Boolean isDeleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM products WHERE uuid = ?", Boolean.class, productUuid);

        assertThat(isDeleted).isTrue();
    }

    @Test
    void shouldCreateProductWithExactPriceNoRounding() throws Exception {
        // Given: Un precio con decimales específicos
        BigDecimal exactPrice = new BigDecimal("10.99");
        ProductRequest request = ProductRequest.builder()
                .name("Producto Precio Exacto")
                .price(exactPrice)
                .categoryId(mockCategory.getId())
                .build();

        // When
        String responseContent = mockMvc.perform(post("/api/v1/admin/products")
                        .header("X-Tenant", "kfc")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(10.99))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID productUuid = UUID.fromString(objectMapper.readTree(responseContent).get("uuid").asText());

        // Then: Validar en BD física que el valor DECIMAL no tiene errores de redondeo
        TenantContext.clear();
        BigDecimal priceInDb = jdbcTemplate.queryForObject(
                "SELECT price FROM products WHERE uuid = ?", BigDecimal.class, productUuid);

        assertThat(priceInDb).isEqualByComparingTo(exactPrice);
    }

    @Test
    void shouldFilterUnavailableOrDeletedProductsInPublicCatalog() throws Exception {
        // Given:
        // 1. Producto Disponible
        ProductRequest p1 = ProductRequest.builder().name("Pollo Disponible").price(new BigDecimal("5.00")).categoryId(mockCategory.getId()).build();
        String res1 = createProductRequest(p1);
        UUID uuid1 = UUID.fromString(objectMapper.readTree(res1).get("uuid").asText());

        // 2. Producto No Disponible (is_available = false)
        ProductRequest p2 = ProductRequest.builder().name("Pollo No Disponible").price(new BigDecimal("6.00")).categoryId(mockCategory.getId()).build();
        String res2 = createProductRequest(p2);
        UUID uuid2 = UUID.fromString(objectMapper.readTree(res2).get("uuid").asText());
        // Toggle para poner disponible en false
        mockMvc.perform(patch("/api/v1/admin/products/" + uuid2 + "/toggle-availability")
                        .header("X-Tenant", "kfc")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());

        // 3. Producto Eliminado (deleted = true)
        ProductRequest p3 = ProductRequest.builder().name("Pollo Eliminado").price(new BigDecimal("7.00")).categoryId(mockCategory.getId()).build();
        String res3 = createProductRequest(p3);
        UUID uuid3 = UUID.fromString(objectMapper.readTree(res3).get("uuid").asText());
        // Borrar producto
        mockMvc.perform(delete("/api/v1/admin/products/" + uuid3)
                        .header("X-Tenant", "kfc")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNoContent());

        // When: Consultar catálogo PÚBLICO del comensal (sin Authorization token y libre acceso)
        mockMvc.perform(get("/api/v1/menu/catalog")
                        .header("X-Tenant", "kfc"))
                .andExpect(status().isOk())
                // Then: Solo debe retornar 1 producto en el catálogo (el producto disponible)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].uuid").value(uuid1.toString()))
                .andExpect(jsonPath("$[0].name").value("Pollo Disponible"));
    }

    private String createProductRequest(ProductRequest request) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/products")
                        .header("X-Tenant", "kfc")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
