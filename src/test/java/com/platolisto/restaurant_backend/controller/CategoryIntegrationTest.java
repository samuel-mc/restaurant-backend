package com.platolisto.restaurant_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platolisto.restaurant_backend.dto.CategoryRequest;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.User;
import com.platolisto.restaurant_backend.entity.UserRole;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.CategoryRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CategoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Restaurant mockRestaurant;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        cleanup();

        // 1. Crear Restaurante
        mockRestaurant = restaurantRepository.save(Restaurant.builder()
                .name("Pizza Hut")
                .subdomain("pizzahut")
                .isActive(true)
                .build());

        // 2. Crear Administrador
        User mockUser = userRepository.save(User.builder()
                .restaurant(mockRestaurant)
                .name("Carlos Gomez")
                .email("carlos@pizzahut.com")
                .passwordHash(passwordEncoder.encode("Secret123"))
                .role(UserRole.ADMIN)
                .isActive(true)
                .build());

        // 3. Generar JWT para el administrador
        org.springframework.security.core.userdetails.User springUser = 
                new org.springframework.security.core.userdetails.User(
                        mockUser.getEmail(), 
                        mockUser.getPasswordHash(), 
                        java.util.Collections.emptyList()
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
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    void shouldCreateCategorySuccessfully() throws Exception {
        // Given
        CategoryRequest request = new CategoryRequest("Entradas", 1);

        // When & Then
        String responseContent = mockMvc.perform(post("/api/v1/admin/categories")
                        .header("X-Tenant", "pizzahut")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Entradas"))
                .andExpect(jsonPath("$.displayOrder").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long categoryId = objectMapper.readTree(responseContent).get("id").asLong();

        // Limpiar el contexto para poder consultar libremente sin el filtro de Hibernate activo en la consulta directa
        TenantContext.clear();

        // Validar directamente en base de datos que se haya asignado el ID del restaurante correcto
        Long assignedRestaurantId = jdbcTemplate.queryForObject(
                "SELECT restaurant_id FROM categories WHERE id = ?", Long.class, categoryId);
        
        assertThat(assignedRestaurantId).isEqualTo(mockRestaurant.getId());
    }

    @Test
    void shouldGetCategoriesSortedByDisplayOrder() throws Exception {
        // Given: Insertar en desorden
        CategoryRequest cat1 = new CategoryRequest("Bebidas", 5);
        CategoryRequest cat2 = new CategoryRequest("Pizzas", 1);
        CategoryRequest cat3 = new CategoryRequest("Postres", 3);

        createCategoryRequest(cat1);
        createCategoryRequest(cat2);
        createCategoryRequest(cat3);

        // When & Then: Listamos categorías del administrador (debemos obtenerlas en orden: 1, 3, 5)
        mockMvc.perform(get("/api/v1/admin/categories")
                        .header("X-Tenant", "pizzahut")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Pizzas")) // displayOrder: 1
                .andExpect(jsonPath("$[1].name").value("Postres")) // displayOrder: 3
                .andExpect(jsonPath("$[2].name").value("Bebidas")); // displayOrder: 5
    }

    @Test
    void shouldSoftDeleteCategory() throws Exception {
        // Given: Crear una categoría
        CategoryRequest request = new CategoryRequest("Pasta", 2);
        String responseContent = createCategoryRequest(request);
        Long categoryId = objectMapper.readTree(responseContent).get("id").asLong();

        // When: Borrar la categoría
        mockMvc.perform(delete("/api/v1/admin/categories/" + categoryId)
                        .header("X-Tenant", "pizzahut")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNoContent());

        // Then: Listar categorías y comprobar que ya no retorna la pasta (debido a @SQLRestriction)
        mockMvc.perform(get("/api/v1/admin/categories")
                        .header("X-Tenant", "pizzahut")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Limpiar el TenantContext de la petición para poder consultar libremente en el test
        TenantContext.clear();

        // Validar directamente en base de datos que el campo 'deleted' cambió físicamente a true
        Boolean isDeleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM categories WHERE id = ?", Boolean.class, categoryId);

        assertThat(isDeleted).isTrue();
    }

    private String createCategoryRequest(CategoryRequest request) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/categories")
                        .header("X-Tenant", "pizzahut")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
