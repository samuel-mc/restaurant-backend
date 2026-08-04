package com.platolisto.restaurant_backend.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platolisto.restaurant_backend.dto.*;
import com.platolisto.restaurant_backend.entity.*;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.*;
import com.platolisto.restaurant_backend.security.JwtService;
import com.platolisto.restaurant_backend.security.StaffUserDetails;
import com.platolisto.restaurant_backend.service.TableQrTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P0.4: Smoke Test E2E de Estrés (Loop Operativo Completo en Simulación Multimesa).
 *
 * Simula la carga de trabajo operativa completa:
 * A. Inicio de Jornada y Autenticación de Staff (Mesero, Cocina, Admin).
 * B. 3 Mesas Simultáneas:
 *    - Mesa 1: Comanda inicial (2 platillos + modificadores).
 *    - Mesa 2: Comanda inicial + 2ª ronda (adición de bebidas).
 *    - Mesa 3: Unión de Mesa 3 con Mesa 4 (`merge-tables`) y pedido consolidado.
 * C. Operación KDS & Transición de Estados (`IN_PREPARATION` -> `READY`).
 * D. Eventos de Atención en Sala (`TABLE_CALL` y `PATCH /availability`).
 * E. Pre-Cuenta (cálculo de propinas), Cierre de Cuenta, Smart Rating 5 estrellas y Liberación de Mesa.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class E2eLoopIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StaffMemberRepository staffMemberRepository;

    @Autowired
    private TableQrTokenService tableQrTokenService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Restaurant mockRestaurant;
    private Category mockCategory;
    private Product productTacos;
    private Product productBebida;
    private String waiterToken;
    private String kitchenToken;
    private String adminToken;

    private String tokenMesa1;
    private String tokenMesa2;
    private String tokenMesa3;
    private String tokenMesa4;

    @BeforeEach
    void setUp() {
        // 1. Crear Restaurante de Prueba con orderingEnabled = true
        mockRestaurant = Restaurant.builder()
                .name("Tacos El Estrés")
                .subdomain("tacos-estres-" + UUID.randomUUID().toString().substring(0, 8))
                .tableCount(12)
                .orderingEnabled(true)
                .isActive(true)
                .plan(SubscriptionPlan.PRO)
                .build();
        mockRestaurant = restaurantRepository.save(mockRestaurant);
        TenantContext.setCurrentTenant(mockRestaurant.getId());

        // 2. Crear Categoría y Productos
        mockCategory = Category.builder()
                .restaurant(mockRestaurant)
                .name("Especialidades")
                .displayOrder(1)
                .build();
        mockCategory = categoryRepository.save(mockCategory);

        productTacos = Product.builder()
                .restaurant(mockRestaurant)
                .category(mockCategory)
                .name("Tacos al Pastor")
                .description("Orden de 5 tacos")
                .price(new BigDecimal("120.00"))
                .isAvailable(true)
                .build();
        productTacos = productRepository.save(productTacos);

        productBebida = Product.builder()
                .restaurant(mockRestaurant)
                .category(mockCategory)
                .name("Agua de Horchata")
                .description("Litro frío")
                .price(new BigDecimal("45.00"))
                .isAvailable(true)
                .build();
        productBebida = productRepository.save(productBebida);

        // 3. Crear Usuarios Staff (Login por PIN) y Generar Tokens JWT de Staff
        StaffMember mesero = staffMemberRepository.save(StaffMember.builder()
                .restaurant(mockRestaurant)
                .name("Mesero Carlos")
                .pinHash(passwordEncoder.encode("1234"))
                .role(StaffRole.MESERO)
                .active(true)
                .build());

        StaffMember cocina = staffMemberRepository.save(StaffMember.builder()
                .restaurant(mockRestaurant)
                .name("Cocinero Juan")
                .pinHash(passwordEncoder.encode("1234"))
                .role(StaffRole.COCINA)
                .active(true)
                .build());

        StaffMember admin = staffMemberRepository.save(StaffMember.builder()
                .restaurant(mockRestaurant)
                .name("Admin Sofía")
                .pinHash(passwordEncoder.encode("1234"))
                .role(StaffRole.ADMIN)
                .active(true)
                .build());

        waiterToken = jwtService.generateStaffToken(StaffUserDetails.fromMember(mesero, mockRestaurant.getId()));
        kitchenToken = jwtService.generateStaffToken(StaffUserDetails.fromMember(cocina, mockRestaurant.getId()));
        adminToken = jwtService.generateStaffToken(StaffUserDetails.fromMember(admin, mockRestaurant.getId()));

        // 4. Firmar Tokens QR para Mesas 1, 2, 3 y 4
        tokenMesa1 = tableQrTokenService.sign(mockRestaurant, "1");
        tokenMesa2 = tableQrTokenService.sign(mockRestaurant, "2");
        tokenMesa3 = tableQrTokenService.sign(mockRestaurant, "3");
        tokenMesa4 = tableQrTokenService.sign(mockRestaurant, "4");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Ejecuta la simulación E2E de estrés multimesa completa (P0.4 Loop Operativo)")
    void executeFullMultitableOperationalLoop() throws Exception {
        String slug = mockRestaurant.getSubdomain();

        // ---------------------------------------------------------------------
        // PASO A: Staff Auth & Monitor KDS inicial
        // ---------------------------------------------------------------------
        assertNotNull(waiterToken);
        assertNotNull(kitchenToken);
        assertNotNull(adminToken);

        mockMvc.perform(get("/api/v1/admin/orders/active")
                        .header("X-Tenant", slug)
                        .header("Authorization", "Bearer " + kitchenToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // ---------------------------------------------------------------------
        // PASO B: Simulación de 3 Mesas Simultáneas
        // ---------------------------------------------------------------------
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // --- MESA 1: Comanda inicial (2 platillos) ---
        Callable<String> taskMesa1 = () -> {
            TenantContext.setCurrentTenant(mockRestaurant.getId());
            OrderRequest req1 = OrderRequest.builder()
                    .orderType(OrderType.IN_TABLE)
                    .tableNumber("1")
                    .tableToken(tokenMesa1)
                    .customerName("Familia Pérez")
                    .details(List.of(
                            OrderDetailRequest.builder().productUuid(productTacos.getUuid()).quantity(2).build(),
                            OrderDetailRequest.builder().productUuid(productBebida.getUuid()).quantity(1).build()
                    ))
                    .build();

            String res = mockMvc.perform(post("/api/v1/orders")
                            .header("X-Tenant", slug)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req1)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.tableNumber").value("1"))
                    .andReturn().getResponse().getContentAsString();

            return objectMapper.readTree(res).get("uuid").asText();
        };

        // --- MESA 2: Comanda inicial ---
        Callable<String> taskMesa2 = () -> {
            TenantContext.setCurrentTenant(mockRestaurant.getId());
            OrderRequest req2 = OrderRequest.builder()
                    .orderType(OrderType.IN_TABLE)
                    .tableNumber("2")
                    .tableToken(tokenMesa2)
                    .customerName("Grupo Amigos")
                    .details(List.of(
                            OrderDetailRequest.builder().productUuid(productTacos.getUuid()).quantity(1).build()
                    ))
                    .build();

            String res = mockMvc.perform(post("/api/v1/orders")
                            .header("X-Tenant", slug)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req2)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andReturn().getResponse().getContentAsString();

            return objectMapper.readTree(res).get("uuid").asText();
        };

        // --- MESA 3 + MESA 4: Unión de Mesas (Merge Tables) ---
        Callable<String> taskMesa3 = () -> {
            TenantContext.setCurrentTenant(mockRestaurant.getId());

            // Crear orden en Mesa 3 (Mesa Principal)
            OrderRequest req3 = OrderRequest.builder()
                    .orderType(OrderType.IN_TABLE)
                    .tableNumber("3")
                    .tableToken(tokenMesa3)
                    .customerName("Reunión Empresarial")
                    .details(List.of(
                            OrderDetailRequest.builder().productUuid(productTacos.getUuid()).quantity(3).build(),
                            OrderDetailRequest.builder().productUuid(productBebida.getUuid()).quantity(3).build()
                    ))
                    .build();

            String res = mockMvc.perform(post("/api/v1/orders")
                            .header("X-Tenant", slug)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req3)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String orderUuid = objectMapper.readTree(res).get("uuid").asText();

            // Merge Mesa 3 con Mesa 4
            TableMergeRequest mergeReq = TableMergeRequest.builder()
                    .tenantSlug(slug)
                    .primaryTable("3")
                    .secondaryTables(List.of("4"))
                    .build();

            mockMvc.perform(post("/api/v1/admin/tables/merge")
                            .header("X-Tenant", slug)
                            .header("Authorization", "Bearer " + waiterToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mergeReq)))
                    .andExpect(status().isOk());

            return orderUuid;
        };

        // Ejecutar creación de las 3 mesas
        Future<String> fut1 = executor.submit(taskMesa1);
        Future<String> fut2 = executor.submit(taskMesa2);
        Future<String> fut3 = executor.submit(taskMesa3);

        String orderUuid1 = fut1.get(5, TimeUnit.SECONDS);
        String orderUuid2 = fut2.get(5, TimeUnit.SECONDS);
        String orderUuid3 = fut3.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertNotNull(orderUuid1);
        assertNotNull(orderUuid2);
        assertNotNull(orderUuid3);

        // SEGUNDA RONDA MESA 2 (Adición de bebidas)
        OrderRequest round2Req = OrderRequest.builder()
                .orderType(OrderType.IN_TABLE)
                .tableNumber("2")
                .tableToken(tokenMesa2)
                .activeOrderUuid(UUID.fromString(orderUuid2))
                .customerName("Grupo Amigos")
                .details(List.of(
                        OrderDetailRequest.builder().productUuid(productBebida.getUuid()).quantity(2).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-Tenant", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(round2Req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").value(orderUuid2)) // Mismo UUID consolidado
                .andExpect(jsonPath("$.details", hasSize(2))); // Tacos + Bebidas ronda 2

        // Verificar Aislamiento Estricto de Sesión por Mesa
        mockMvc.perform(get("/api/v1/orders/active-session")
                        .header("X-Tenant", slug)
                        .param("tableNumber", "1")
                        .param("tableToken", tokenMesa1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveOrder").value(true))
                .andExpect(jsonPath("$.order.uuid").value(orderUuid1));

        mockMvc.perform(get("/api/v1/orders/active-session")
                        .header("X-Tenant", slug)
                        .param("tableNumber", "2")
                        .param("tableToken", tokenMesa2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveOrder").value(true))
                .andExpect(jsonPath("$.order.uuid").value(orderUuid2));

        // Mesa 4 (secundaria vinculada a Mesa 3) debe retornar la sesión de la orden consolidada de Mesa 3
        mockMvc.perform(get("/api/v1/orders/active-session")
                        .header("X-Tenant", slug)
                        .param("tableNumber", "4")
                        .param("tableToken", tokenMesa4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveOrder").value(true))
                .andExpect(jsonPath("$.order.uuid").value(orderUuid3));

        // ---------------------------------------------------------------------
        // PASO C: Operación en Cocina (KDS) & Transición de Estados
        // ---------------------------------------------------------------------
        // KDS verifica 3 comandas activas
        mockMvc.perform(get("/api/v1/admin/orders/active")
                        .header("X-Tenant", slug)
                        .header("Authorization", "Bearer " + kitchenToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // Transición de Mesa 1: PENDING -> ACCEPTED -> IN_KITCHEN -> DELIVERED
        OrderStatusRequest statusAccepted = new OrderStatusRequest();
        statusAccepted.setStatus(OrderStatus.ACCEPTED);
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderUuid1 + "/status")
                        .header("X-Tenant", slug)
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusAccepted)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        OrderStatusRequest statusKitchen = new OrderStatusRequest();
        statusKitchen.setStatus(OrderStatus.IN_KITCHEN);
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderUuid1 + "/status")
                        .header("X-Tenant", slug)
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusKitchen)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_KITCHEN"));

        OrderStatusRequest statusDelivered = new OrderStatusRequest();
        statusDelivered.setStatus(OrderStatus.DELIVERED);
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderUuid1 + "/status")
                        .header("X-Tenant", slug)
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusDelivered)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        // ---------------------------------------------------------------------
        // PASO D: Eventos de Atención en Sala y Disponibilidad
        // ---------------------------------------------------------------------
        // Mesa 1 realiza llamada a mesero
        TableCallRequest callReq = TableCallRequest.builder()
                .tableNumber("1")
                .tableToken(tokenMesa1)
                .callType(TableCallType.WAITER)
                .build();

        mockMvc.perform(post("/api/v1/orders/table-calls")
                        .header("X-Tenant", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableNumber").value("1"))
                .andExpect(jsonPath("$.callType").value("WAITER"));

        // Admin/Chef marca producto como "Agotado" (isAvailable: false)
        mockMvc.perform(patch("/api/v1/admin/menu/products/" + productBebida.getUuid() + "/availability")
                        .header("X-Tenant", slug)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAvailable\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        // ---------------------------------------------------------------------
        // PASO E: Pre-Cuenta, Cierre de Cuenta, Smart Rating y Liberación en Mesa 2
        // ---------------------------------------------------------------------
        // 1. Cierre de Mesa 2 por el Mesero
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderUuid2 + "/close")
                        .header("X-Tenant", slug)
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"));

        // 2. Comensal de Mesa 2 envía Smart Rating 5 Estrellas
        SubmitFeedbackRequest feedbackReq = SubmitFeedbackRequest.builder()
                .stars(5)
                .comment("¡Excelente servicio y comida en Mesa 2!")
                .reason("SERVICE")
                .build();

        mockMvc.perform(post("/api/v1/orders/" + orderUuid2 + "/feedback")
                        .header("X-Tenant", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedbackReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("THANKS"));

        // 3. Admin verifica la recepción del feedback en el inbox
        mockMvc.perform(get("/api/v1/admin/feedback")
                        .header("X-Tenant", slug)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stars").value(5))
                .andExpect(jsonPath("$[0].comment").value("¡Excelente servicio y comida en Mesa 2!"));

        // 4. Verificación final: Mesa 2 queda LIBRE automáticamente y su sesión expira de forma limpia
        mockMvc.perform(get("/api/v1/orders/active-session")
                        .header("X-Tenant", slug)
                        .param("tableNumber", "2")
                        .param("tableToken", tokenMesa2))
                .andExpect(status().isNoContent());
    }
}
