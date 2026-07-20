package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.OrderDetailRequest;
import com.platolisto.restaurant_backend.dto.OrderDetailResponse;
import com.platolisto.restaurant_backend.dto.OrderRequest;
import com.platolisto.restaurant_backend.dto.OrderResponse;
import com.platolisto.restaurant_backend.entity.Order;
import com.platolisto.restaurant_backend.entity.OrderDetail;
import com.platolisto.restaurant_backend.entity.OrderStatus;
import com.platolisto.restaurant_backend.entity.OrderType;
import com.platolisto.restaurant_backend.entity.Product;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.OrderRepository;
import com.platolisto.restaurant_backend.repository.ProductRepository;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final RestaurantRepository restaurantRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("El restaurante asociado no existe."));

        validateOrderTypeAllowed(restaurant, request.getOrderType());

        // Inicializar Pedido
        Order order = Order.builder()
                .restaurant(restaurant)
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .orderType(request.getOrderType())
                .tableNumber(request.getTableNumber())
                .deliveryAddress(request.getDeliveryAddress())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .details(new ArrayList<>())
                .build();

        BigDecimal calculatedTotal = BigDecimal.ZERO;

        for (OrderDetailRequest detailRequest : request.getDetails()) {
            Product product = productRepository.findByUuid(detailRequest.getProductUuid())
                    .orElseThrow(() -> new IllegalArgumentException("El producto con UUID " + detailRequest.getProductUuid() + " no existe."));

            // Validar que el producto pertenezca al restaurante actual
            if (!product.getRestaurant().getId().equals(restaurantId)) {
                throw new IllegalArgumentException("El producto con UUID " + detailRequest.getProductUuid() + " no pertenece al restaurante actual.");
            }

            // Validar que el producto esté disponible y no eliminado
            if (product.isDeleted() || !product.isAvailable()) {
                throw new IllegalArgumentException("El producto " + product.getName() + " no está disponible en este momento.");
            }

            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(detailRequest.getQuantity()));
            calculatedTotal = calculatedTotal.add(subtotal);

            OrderDetail detail = OrderDetail.builder()
                    .product(product)
                    .quantity(detailRequest.getQuantity())
                    .unitPrice(unitPrice)
                    .notes(detailRequest.getNotes())
                    .build();

            order.addDetail(detail);
        }

        order.setTotalAmount(calculatedTotal);

        Order savedOrder = orderRepository.save(order);
        log.info("Pedido creado con éxito: ID {}, UUID {}, Total {}", savedOrder.getId(), savedOrder.getUuid(), savedOrder.getTotalAmount());

        OrderResponse response = mapToResponse(savedOrder);
        publishOrderEvents(restaurant, response);
        return response;
    }

    /**
     * Pedidos activos para el monitor de cocina/caja
     * (PENDING, ACCEPTED, IN_KITCHEN) del tenant actual.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getActiveOrders() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.PENDING,
                OrderStatus.ACCEPTED,
                OrderStatus.IN_KITCHEN
        );

        return orderRepository.findActiveWithDetails(activeStatuses).stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Consulta pública de un pedido por UUID, acotada al tenant actual.
     * Usada por la pantalla de tracking del comensal para el estado inicial.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderByUuid(UUID uuid) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        Order order = orderRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el pedido con UUID: " + uuid));

        if (!order.getRestaurant().getId().equals(restaurantId)) {
            throw new IllegalArgumentException("No se encontró el pedido con UUID: " + uuid);
        }

        return mapToResponse(order);
    }

    private static void validateOrderTypeAllowed(Restaurant restaurant, OrderType orderType) {
        if (orderType == null) {
            throw new IllegalArgumentException("El tipo de pedido es requerido.");
        }
        switch (orderType) {
            case IN_TABLE -> {
                // En salón siempre permitido.
            }
            case PICKUP -> {
                if (!restaurant.isHasPickup()) {
                    throw new IllegalArgumentException(
                            "Este restaurante no tiene habilitado el módulo de recoger (pickup)."
                    );
                }
            }
            case DELIVERY -> {
                if (!restaurant.isHasDelivery()) {
                    throw new IllegalArgumentException(
                            "Este restaurante no tiene habilitado el módulo de delivery."
                    );
                }
            }
        }
    }

    @Transactional
    public OrderResponse updateOrderStatus(UUID uuid, OrderStatus status) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        Order order = orderRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el pedido con UUID: " + uuid));

        if (!order.getRestaurant().getId().equals(restaurantId)) {
            throw new IllegalArgumentException("No se encontró el pedido con UUID: " + uuid);
        }

        order.setStatus(status);
        Order updatedOrder = orderRepository.save(order);
        log.info("Estado del pedido actualizado a {}: UUID {}", status, uuid);

        OrderResponse response = mapToResponse(updatedOrder);
        publishOrderEvents(updatedOrder.getRestaurant(), response);
        return response;
    }

    /**
     * Notifica a cocina (por id y por slug) y al comensal (por UUID del pedido).
     */
    private void publishOrderEvents(Restaurant restaurant, OrderResponse response) {
        Long restaurantId = restaurant.getId();
        String slug = restaurant.getSubdomain();

        String kitchenById = "/topic/restaurants/" + restaurantId + "/orders";
        messagingTemplate.convertAndSend(kitchenById, response);
        log.info("Notificación WebSocket enviada a {}", kitchenById);

        if (slug != null && !slug.isBlank()) {
            String kitchenBySlug = "/topic/admin/" + slug + "/orders";
            messagingTemplate.convertAndSend(kitchenBySlug, response);
            log.info("Notificación WebSocket admin enviada a {}", kitchenBySlug);
        }

        String trackingTopic = "/topic/order/" + response.getUuid();
        messagingTemplate.convertAndSend(trackingTopic, response);
        log.info("Notificación WebSocket de tracking enviada a {}", trackingTopic);
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderDetailResponse> detailsResponse = order.getDetails().stream()
                .map(detail -> OrderDetailResponse.builder()
                        .productUuid(detail.getProduct().getUuid())
                        .productName(detail.getProduct().getName())
                        .quantity(detail.getQuantity())
                        .unitPrice(detail.getUnitPrice())
                        .subtotal(detail.getUnitPrice().multiply(BigDecimal.valueOf(detail.getQuantity())))
                        .notes(detail.getNotes())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .uuid(order.getUuid())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .orderType(order.getOrderType())
                .tableNumber(order.getTableNumber())
                .deliveryAddress(order.getDeliveryAddress())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .details(detailsResponse)
                .build();
    }
}
