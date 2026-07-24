package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.ActiveSessionResponse;
import com.platolisto.restaurant_backend.dto.AdminOrderListFilter;
import com.platolisto.restaurant_backend.dto.OrderDetailRequest;
import com.platolisto.restaurant_backend.dto.OrderDetailResponse;
import com.platolisto.restaurant_backend.dto.OrderRequest;
import com.platolisto.restaurant_backend.dto.OrderResponse;
import com.platolisto.restaurant_backend.entity.Order;
import com.platolisto.restaurant_backend.entity.OrderDetail;
import com.platolisto.restaurant_backend.entity.OrderItemStatus;
import com.platolisto.restaurant_backend.entity.OrderStatus;
import com.platolisto.restaurant_backend.entity.OrderType;
import com.platolisto.restaurant_backend.entity.Product;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.OrderRepository;
import com.platolisto.restaurant_backend.repository.ProductRepository;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final List<OrderStatus> OPEN_STATUSES = List.of(
            OrderStatus.PENDING,
            OrderStatus.ACCEPTED,
            OrderStatus.IN_KITCHEN,
            OrderStatus.DELIVERED
    );

    /** Comandas visibles en el monitor de cocina / caja (hasta cobro). */
    private static final List<OrderStatus> KITCHEN_ACTIVE_STATUSES = List.of(
            OrderStatus.PENDING,
            OrderStatus.ACCEPTED,
            OrderStatus.IN_KITCHEN,
            OrderStatus.DELIVERED
    );

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

        if (!restaurant.isOrderingEnabled()) {
            throw new IllegalArgumentException(
                    "Este restaurante no acepta pedidos desde el menú digital. El menú es solo consulta."
            );
        }

        validateOrderTypeAllowed(restaurant, request.getOrderType());

        Order openOrder = findOpenOrderForAddition(request, restaurantId);
        if (openOrder != null) {
            return appendRound(openOrder, request, restaurantId, restaurant);
        }

        return createFreshOrder(request, restaurantId, restaurant);
    }

    private OrderResponse createFreshOrder(
            OrderRequest request,
            Long restaurantId,
            Restaurant restaurant
    ) {
        Order order = Order.builder()
                .restaurant(restaurant)
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .orderType(request.getOrderType())
                .tableNumber(normalizeTable(request.getTableNumber()))
                .deliveryAddress(request.getDeliveryAddress())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .details(new ArrayList<>())
                .build();

        BigDecimal calculatedTotal = addDetailsToOrder(order, request.getDetails(), restaurantId, 1);
        order.setTotalAmount(calculatedTotal);

        Order savedOrder = orderRepository.save(order);
        log.info(
                "Pedido creado: UUID={}, mesa={}, total={}",
                savedOrder.getUuid(),
                savedOrder.getTableNumber(),
                savedOrder.getTotalAmount()
        );

        OrderResponse response = mapToResponse(savedOrder);
        publishOrderEvents(restaurant, response);
        return response;
    }

    private OrderResponse appendRound(
            Order order,
            OrderRequest request,
            Long restaurantId,
            Restaurant restaurant
    ) {
        int nextBatch = order.getDetails().stream()
                .mapToInt(OrderDetail::getBatchNumber)
                .max()
                .orElse(0) + 1;

        BigDecimal added = addDetailsToOrder(order, request.getDetails(), restaurantId, nextBatch);
        order.setTotalAmount(order.getTotalAmount().add(added));

        // Trae la comanda a "Recibidos" para que cocina note la adición.
        if (order.getStatus() == OrderStatus.ACCEPTED
                || order.getStatus() == OrderStatus.IN_KITCHEN
                || order.getStatus() == OrderStatus.DELIVERED) {
            order.setStatus(OrderStatus.PENDING);
        }

        Order saved = orderRepository.save(order);
        log.info(
                "Adición ronda {} a pedido UUID={}, +{}, total={}",
                nextBatch,
                saved.getUuid(),
                added,
                saved.getTotalAmount()
        );

        OrderResponse response = mapToResponse(saved);
        publishOrderEvents(restaurant, response);
        return response;
    }

    private BigDecimal addDetailsToOrder(
            Order order,
            List<OrderDetailRequest> detailRequests,
            Long restaurantId,
            int batchNumber
    ) {
        BigDecimal added = BigDecimal.ZERO;
        for (OrderDetailRequest detailRequest : detailRequests) {
            Product product = productRepository.findByUuid(detailRequest.getProductUuid())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El producto con UUID " + detailRequest.getProductUuid() + " no existe."
                    ));

            if (!product.getRestaurant().getId().equals(restaurantId)) {
                throw new IllegalArgumentException(
                        "El producto con UUID " + detailRequest.getProductUuid()
                                + " no pertenece al restaurante actual."
                );
            }
            if (product.isDeleted() || !product.isAvailable()) {
                throw new IllegalArgumentException(
                        "El producto " + product.getName() + " no está disponible en este momento."
                );
            }

            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(detailRequest.getQuantity()));
            added = added.add(subtotal);

            OrderDetail detail = OrderDetail.builder()
                    .product(product)
                    .quantity(detailRequest.getQuantity())
                    .unitPrice(unitPrice)
                    .notes(detailRequest.getNotes())
                    .batchNumber(batchNumber)
                    .status(OrderItemStatus.PENDING)
                    .build();

            order.addDetail(detail);
        }
        return added;
    }

    /**
     * Busca orden abierta: primero por UUID de sesión; si no, por mesa (IN_TABLE).
     */
    private Order findOpenOrderForAddition(OrderRequest request, Long restaurantId) {
        if (request.getActiveOrderUuid() != null) {
            Order byUuid = orderRepository.findByUuidWithDetails(request.getActiveOrderUuid())
                    .orElse(null);
            if (byUuid != null
                    && byUuid.getRestaurant().getId().equals(restaurantId)
                    && OPEN_STATUSES.contains(byUuid.getStatus())) {
                return byUuid;
            }
        }

        if (request.getOrderType() != OrderType.IN_TABLE) {
            return null;
        }

        String table = normalizeTable(request.getTableNumber());
        if (table == null) {
            return null;
        }

        return orderRepository
                .findOpenInTableWithDetails(table, OrderType.IN_TABLE, OPEN_STATUSES)
                .stream()
                .max(Comparator.comparing(Order::getCreatedAt))
                .orElse(null);
    }

    private static String normalizeTable(String tableNumber) {
        if (tableNumber == null) {
            return null;
        }
        String trimmed = tableNumber.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional(readOnly = true)
    public ActiveSessionResponse getActiveSessionByTable(String tableNumber) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        String table = normalizeTable(tableNumber);
        if (table == null) {
            return ActiveSessionResponse.builder().hasActiveOrder(false).build();
        }

        Order open = orderRepository
                .findOpenInTableWithDetails(table, OrderType.IN_TABLE, OPEN_STATUSES)
                .stream()
                .max(Comparator.comparing(Order::getCreatedAt))
                .orElse(null);

        if (open == null || !open.getRestaurant().getId().equals(restaurantId)) {
            return ActiveSessionResponse.builder().hasActiveOrder(false).build();
        }

        return ActiveSessionResponse.builder()
                .hasActiveOrder(true)
                .order(mapToResponse(open))
                .build();
    }

    @Transactional
    public OrderResponse closeOrder(UUID uuid) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        Order order = orderRepository.findByUuidWithDetails(uuid)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el pedido con UUID: " + uuid));

        if (!order.getRestaurant().getId().equals(restaurantId)) {
            throw new IllegalArgumentException("No se encontró el pedido con UUID: " + uuid);
        }

        if (order.getStatus() == OrderStatus.CLOSED) {
            return mapToResponse(order);
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("No se puede cerrar un pedido cancelado.");
        }

        order.setStatus(OrderStatus.CLOSED);
        for (OrderDetail detail : order.getDetails()) {
            if (detail.getStatus() != OrderItemStatus.DELIVERED) {
                detail.setStatus(OrderItemStatus.DELIVERED);
            }
        }

        Order saved = orderRepository.save(order);
        log.info("Cuenta cerrada (CLOSED): UUID={}, mesa={}", saved.getUuid(), saved.getTableNumber());

        OrderResponse response = mapToResponse(saved);
        publishOrderEvents(saved.getRestaurant(), response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getActiveOrders() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        return orderRepository.findActiveWithDetails(KITCHEN_ACTIVE_STATUSES).stream()
                .sorted(Comparator.comparing(Order::getCreatedAt))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Listado paginado para panel de cuentas.
     * Prioridad: {@code filter} de UI; si no, {@code status}/{@code orderType} sueltos.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(
            AdminOrderListFilter filter,
            OrderStatus status,
            OrderType orderType,
            Pageable pageable
    ) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        List<OrderStatus> statuses = null;
        OrderType resolvedType = orderType;

        if (filter != null) {
            switch (filter) {
                case OPEN -> {
                    statuses = OPEN_STATUSES;
                    resolvedType = OrderType.IN_TABLE;
                }
                case CLOSED -> {
                    statuses = List.of(OrderStatus.CLOSED);
                    resolvedType = null;
                }
                case PICKUP -> {
                    statuses = null;
                    resolvedType = OrderType.PICKUP;
                }
                case ALL -> {
                    statuses = null;
                    resolvedType = null;
                }
            }
        } else if (status != null) {
            statuses = List.of(status);
        }

        List<OrderStatus> statusFilter = statuses;
        OrderType typeFilter = resolvedType;

        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (statusFilter != null && !statusFilter.isEmpty()) {
                predicates.add(root.get("status").in(statusFilter));
            }
            if (typeFilter != null) {
                predicates.add(cb.equal(root.get("orderType"), typeFilter));
            }
            if (query != null) {
                query.distinct(true);
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<Order> page = orderRepository.findAll(spec, pageable);
        List<Order> content = page.getContent();
        if (content.isEmpty()) {
            return page.map(this::mapToResponse);
        }

        List<Long> ids = content.stream().map(Order::getId).toList();
        Map<Long, Order> hydratedById = orderRepository.findByIdInWithDetails(ids).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity(), (a, b) -> a));

        List<OrderResponse> responses = content.stream()
                .map(order -> mapToResponse(hydratedById.getOrDefault(order.getId(), order)))
                .toList();

        return new PageImpl<>(responses, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByUuid(UUID uuid) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        Order order = orderRepository.findByUuidWithDetails(uuid)
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

        Order order = orderRepository.findByUuidWithDetails(uuid)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el pedido con UUID: " + uuid));

        if (!order.getRestaurant().getId().equals(restaurantId)) {
            throw new IllegalArgumentException("No se encontró el pedido con UUID: " + uuid);
        }

        order.setStatus(status);

        // Al marcar la orden completa como entregada o cerrada, cierra ítems pendientes.
        if (status == OrderStatus.DELIVERED || status == OrderStatus.CLOSED) {
            for (OrderDetail detail : order.getDetails()) {
                if (detail.getStatus() != OrderItemStatus.DELIVERED) {
                    detail.setStatus(OrderItemStatus.DELIVERED);
                }
            }
        } else if (status == OrderStatus.IN_KITCHEN || status == OrderStatus.ACCEPTED) {
            for (OrderDetail detail : order.getDetails()) {
                if (detail.getStatus() == OrderItemStatus.PENDING) {
                    detail.setStatus(OrderItemStatus.PREPARING);
                }
            }
        }

        Order updatedOrder = orderRepository.save(order);
        log.info("Estado del pedido actualizado a {}: UUID {}", status, uuid);

        OrderResponse response = mapToResponse(updatedOrder);
        publishOrderEvents(updatedOrder.getRestaurant(), response);
        return response;
    }

    @Transactional
    public OrderResponse updateOrderItemStatus(UUID orderUuid, Long detailId, OrderItemStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("El estado del ítem es requerido.");
        }

        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        Order order = orderRepository.findByUuidWithDetails(orderUuid)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el pedido con UUID: " + orderUuid));

        if (!order.getRestaurant().getId().equals(restaurantId)) {
            throw new IllegalArgumentException("No se encontró el pedido con UUID: " + orderUuid);
        }

        OrderDetail detail = order.getDetails().stream()
                .filter(d -> d.getId().equals(detailId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el ítem en el pedido."));

        detail.setStatus(status);
        Order saved = orderRepository.save(order);
        log.info(
                "Ítem {} del pedido {} → {}",
                detailId,
                orderUuid,
                status
        );

        OrderResponse response = mapToResponse(saved);
        publishOrderEvents(saved.getRestaurant(), response);
        return response;
    }

    private void publishOrderEvents(Restaurant restaurant, OrderResponse response) {
        Long restaurantId = restaurant.getId();
        String slug = restaurant.getSubdomain();

        String kitchenById = "/topic/restaurants/" + restaurantId + "/orders";
        messagingTemplate.convertAndSend(kitchenById, response);

        if (slug != null && !slug.isBlank()) {
            String kitchenBySlug = "/topic/admin/" + slug + "/orders";
            messagingTemplate.convertAndSend(kitchenBySlug, response);
        }

        String trackingTopic = "/topic/order/" + response.getUuid();
        messagingTemplate.convertAndSend(trackingTopic, response);
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderDetailResponse> detailsResponse = order.getDetails().stream()
                .sorted(Comparator
                        .comparingInt(OrderDetail::getBatchNumber)
                        .thenComparing(OrderDetail::getId, Comparator.nullsLast(Long::compareTo)))
                .map(detail -> {
                    Product product = detail.getProduct();
                    return OrderDetailResponse.builder()
                            .id(detail.getId())
                            .productUuid(product != null ? product.getUuid() : null)
                            .productName(product != null ? product.getName() : "Producto no disponible")
                            .quantity(detail.getQuantity())
                            .unitPrice(detail.getUnitPrice())
                            .subtotal(detail.getUnitPrice().multiply(BigDecimal.valueOf(detail.getQuantity())))
                            .notes(detail.getNotes())
                            .batchNumber(detail.getBatchNumber())
                            .status(detail.getStatus() != null ? detail.getStatus() : OrderItemStatus.PENDING)
                            .build();
                })
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .uuid(order.getUuid())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .orderType(order.getOrderType())
                .tableNumber(order.getTableNumber())
                .deliveryAddress(order.getDeliveryAddress())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .details(detailsResponse)
                .build();
    }
}
