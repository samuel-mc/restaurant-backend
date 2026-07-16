package com.platolisto.restaurant_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platolisto.restaurant_backend.dto.OrderDetailRequest;
import com.platolisto.restaurant_backend.dto.OrderRequest;
import com.platolisto.restaurant_backend.dto.OrderResponse;
import com.platolisto.restaurant_backend.entity.Category;
import com.platolisto.restaurant_backend.entity.OrderType;
import com.platolisto.restaurant_backend.entity.Product;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.CategoryRepository;
import com.platolisto.restaurant_backend.repository.ProductRepository;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketOrderIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private OrderService orderService;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Restaurant restaurant1;
    private Restaurant restaurant2;
    private Product productRestaurant1;
    private Product productRestaurant2;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        cleanup();

        // 1. Crear Restaurantes (Tenants)
        restaurant1 = restaurantRepository.save(Restaurant.builder()
                .name("Pizza Hut")
                .subdomain("pizzahut")
                .isActive(true)
                .build());

        restaurant2 = restaurantRepository.save(Restaurant.builder()
                .name("Burger King")
                .subdomain("burgerking")
                .isActive(true)
                .build());

        // 2. Crear Categorías
        Category cat1 = categoryRepository.save(Category.builder()
                .restaurant(restaurant1)
                .name("Pizzas")
                .displayOrder(1)
                .build());

        Category cat2 = categoryRepository.save(Category.builder()
                .restaurant(restaurant2)
                .name("Hamburguesas")
                .displayOrder(1)
                .build());

        // 3. Crear Productos
        productRestaurant1 = productRepository.save(Product.builder()
                .restaurant(restaurant1)
                .category(cat1)
                .name("Pizza Pepperoni")
                .price(new BigDecimal("10.00"))
                .isAvailable(true)
                .build());

        productRestaurant2 = productRepository.save(Product.builder()
                .restaurant(restaurant2)
                .category(cat2)
                .name("Whopper")
                .price(new BigDecimal("6.00"))
                .isAvailable(true)
                .build());

        // Configurar cliente STOMP con ObjectMapper que soporte Java 8 Date/Time
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(mapper);

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(converter);
    }

    @AfterEach
    void tearDown() {
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
    void shouldReceiveOrderCreatedInRealTimeAndIsolateBetweenTenants() throws Exception {
        // --- PARTE 1: Suscribir al Restaurante 1 ---
        CompletableFuture<OrderResponse> completableFutureRestaurant1 = new CompletableFuture<>();

        String wsUrl = "ws://localhost:" + port + "/ws-orders";
        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {
            @Override
            public void handleException(StompSession session, org.springframework.messaging.simp.stomp.StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                System.err.println("STOMP Session Exception: " + exception.getMessage());
                exception.printStackTrace();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                System.err.println("STOMP Transport Error: " + exception.getMessage());
                exception.printStackTrace();
            }
        }).get(5, TimeUnit.SECONDS);

        // Suscribirse al canal exclusivo del restaurante 1
        String topicRestaurant1 = "/topic/restaurants/" + restaurant1.getId() + "/orders";
        session.subscribe(topicRestaurant1, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return OrderResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                completableFutureRestaurant1.complete((OrderResponse) payload);
            }
        });

        // --- PARTE 2: Crear una orden para el Restaurante 2 y verificar que el Restaurante 1 NO reciba nada ---
        OrderDetailRequest itemRestaurant2 = new OrderDetailRequest(productRestaurant2.getUuid(), 1, null);
        OrderRequest orderRequest2 = OrderRequest.builder()
                .customerName("Juan en BurgerKing")
                .orderType(OrderType.PICKUP)
                .details(List.of(itemRestaurant2))
                .build();

        // Configurar tenant context en el hilo del test para la llamada directa al servicio
        TenantContext.setCurrentTenant(restaurant2.getId());
        orderService.createOrder(orderRequest2);
        TenantContext.clear();

        // Verificar que el restaurante 1 no recibió nada (completableFutureRestaurant1 sigue vacío)
        OrderResponse unexpected = null;
        try {
            unexpected = completableFutureRestaurant1.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Se espera TimeoutException porque no debe recibir notificaciones de otro tenant
        }
        assertThat(unexpected).isNull();

        // --- PARTE 3: Crear una orden para el Restaurante 1 y verificar que sí la recibe ---
        OrderDetailRequest itemRestaurant1 = new OrderDetailRequest(productRestaurant1.getUuid(), 1, null);
        OrderRequest orderRequest1 = OrderRequest.builder()
                .customerName("Carlos en PizzaHut")
                .orderType(OrderType.IN_TABLE)
                .tableNumber("4")
                .details(List.of(itemRestaurant1))
                .build();

        // Configurar tenant context para el restaurante 1
        TenantContext.setCurrentTenant(restaurant1.getId());
        orderService.createOrder(orderRequest1);
        TenantContext.clear();

        // Verificar recepción en tiempo real
        OrderResponse receivedNotification = completableFutureRestaurant1.get(5, TimeUnit.SECONDS);
        assertThat(receivedNotification).isNotNull();
        assertThat(receivedNotification.getCustomerName()).isEqualTo("Carlos en PizzaHut");
        assertThat(receivedNotification.getTotalAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
    }
}
