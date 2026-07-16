package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.OrderDetailRequest;
import com.platolisto.restaurant_backend.dto.OrderDetailResponse;
import com.platolisto.restaurant_backend.dto.OrderRequest;
import com.platolisto.restaurant_backend.dto.OrderResponse;
import com.platolisto.restaurant_backend.entity.Order;
import com.platolisto.restaurant_backend.entity.OrderDetail;
import com.platolisto.restaurant_backend.entity.OrderStatus;
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

        // Mapear a respuesta para el WebSocket
        OrderResponse response = mapToResponse(savedOrder);

        // Notificar en tiempo real al canal WebSocket específico del restaurante
        String destination = "/topic/restaurants/" + restaurantId + "/orders";
        messagingTemplate.convertAndSend(destination, response);
        log.info("Notificación WebSocket de creación de pedido enviada a {}", destination);

        return response;
    }

    @Transactional
    public OrderResponse updateOrderStatus(UUID uuid, OrderStatus status) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        Order order = orderRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el pedido con UUID: " + uuid));

        // Actualizar el estado
        order.setStatus(status);
        Order updatedOrder = orderRepository.save(order);
        log.info("Estado del pedido actualizado a {}: UUID {}", status, uuid);

        OrderResponse response = mapToResponse(updatedOrder);

        // Notificar en tiempo real la actualización de estado del pedido
        String destination = "/topic/restaurants/" + restaurantId + "/orders";
        messagingTemplate.convertAndSend(destination, response);
        log.info("Notificación WebSocket de actualización de estado enviada a {}", destination);

        return response;
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
