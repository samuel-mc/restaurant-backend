package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.ActiveSessionResponse;
import com.platolisto.restaurant_backend.dto.AdminOrderListFilter;
import com.platolisto.restaurant_backend.dto.OrderDetailModifierResponse;
import com.platolisto.restaurant_backend.dto.OrderDetailRequest;
import com.platolisto.restaurant_backend.dto.OrderDetailResponse;
import com.platolisto.restaurant_backend.dto.OrderRequest;
import com.platolisto.restaurant_backend.dto.OrderResponse;
import com.platolisto.restaurant_backend.dto.StaffOrderRequest;
import com.platolisto.restaurant_backend.dto.TableMergeRequest;
import com.platolisto.restaurant_backend.entity.Order;
import com.platolisto.restaurant_backend.entity.OrderDetail;
import com.platolisto.restaurant_backend.entity.OrderDetailModifier;
import com.platolisto.restaurant_backend.entity.OrderItemStatus;
import com.platolisto.restaurant_backend.entity.OrderStatus;
import com.platolisto.restaurant_backend.entity.OrderType;
import com.platolisto.restaurant_backend.entity.PaymentStatus;
import com.platolisto.restaurant_backend.entity.Product;
import com.platolisto.restaurant_backend.entity.ProductModifier;
import com.platolisto.restaurant_backend.entity.ProductModifierGroup;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.plan.PlanLimits;
import com.platolisto.restaurant_backend.repository.OrderRepository;
import com.platolisto.restaurant_backend.repository.ProductModifierRepository;
import com.platolisto.restaurant_backend.repository.ProductRepository;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.security.OrderStatusAuthorization;
import com.platolisto.restaurant_backend.security.StaffUserDetails;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
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
    private final ProductModifierRepository productModifierRepository;
    private final RestaurantRepository restaurantRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TableQrTokenService tableQrTokenService;

    private static final Pattern MESA_PREFIX = Pattern.compile("(?i)^mesa\\s*");

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
        validateDeliveryContact(request);
        validateTableAccess(restaurant, request);

        // Si la mesa ya está unida a otra cuenta, no abrir una segunda orden.
        if (request.getOrderType() == OrderType.IN_TABLE && request.getActiveOrderUuid() == null) {
            String table = canonicalizeTable(request.getTableNumber());
            Order covering = findOpenOrderCoveringTable(table, restaurantId);
            if (covering != null) {
                throw new IllegalArgumentException(
                        "Esta mesa ya tiene una cuenta abierta"
                                + (covering.getTableNumber() != null
                                && !covering.getTableNumber().equalsIgnoreCase(table)
                                ? " (unida a Mesa " + covering.getTableNumber() + ")"
                                : "")
                                + ". Usa la sesión activa o pide adición."
                );
            }
        }

        Order openOrder = findOpenOrderForAddition(request, restaurantId);
        if (openOrder != null) {
            return stripPublicPii(appendRound(openOrder, request, restaurantId, restaurant));
        }

        return stripPublicPii(createFreshOrder(request, restaurantId, restaurant, null));
    }

    /**
     * Comanda tomada por el mesero desde el panel: sin token QR.
     * Atribuye staffId/staffName desde el JWT del equipo.
     */
    @Transactional
    public OrderResponse createStaffOrder(StaffOrderRequest request) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("El restaurante asociado no existe."));

        if (!restaurant.isOrderingEnabled()) {
            throw new IllegalArgumentException(
                    "Este restaurante no acepta pedidos. Activa el módulo de pedidos en configuración."
            );
        }

        String table = canonicalizeTable(request.getTableNumber());
        if (table == null) {
            throw new IllegalArgumentException("El número de mesa es requerido.");
        }
        assertTableInFloor(restaurant, table);

        StaffUserDetails staff = currentStaffOrNull();
        String customerName = request.getCustomerName() == null || request.getCustomerName().isBlank()
                ? "Mesa " + table
                : request.getCustomerName().trim();

        OrderRequest asPublic = OrderRequest.builder()
                .customerName(customerName)
                .orderType(OrderType.IN_TABLE)
                .tableNumber(table)
                .activeOrderUuid(request.getActiveOrderUuid())
                .details(request.getDetails())
                .build();

        Order openOrder = findOpenOrderForAddition(asPublic, restaurantId);
        if (openOrder == null && request.getActiveOrderUuid() == null) {
            openOrder = findOpenOrderCoveringTable(table, restaurantId);
        }

        if (openOrder != null) {
            if (staff != null) {
                openOrder.setStaffId(staff.getStaffId());
                openOrder.setStaffName(staff.getName());
            }
            return appendRound(openOrder, asPublic, restaurantId, restaurant);
        }
        return createFreshOrder(asPublic, restaurantId, restaurant, staff);
    }

    @Transactional
    public OrderResponse mergeTables(TableMergeRequest request) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("El restaurante asociado no existe."));

        String primary = canonicalizeTable(request.getPrimaryTable());
        if (primary == null) {
            throw new IllegalArgumentException("La mesa principal es requerida.");
        }
        assertTableInFloor(restaurant, primary);

        LinkedHashSet<String> secondaries = new LinkedHashSet<>();
        for (String raw : request.getSecondaryTables()) {
            String table = canonicalizeTable(raw);
            if (table == null) {
                continue;
            }
            if (table.equalsIgnoreCase(primary)) {
                throw new IllegalArgumentException("No puedes vincular la mesa principal consigo misma.");
            }
            assertTableInFloor(restaurant, table);
            secondaries.add(table);
        }
        if (secondaries.isEmpty()) {
            throw new IllegalArgumentException("Debes indicar al menos una mesa secundaria distinta.");
        }

        for (String secondary : secondaries) {
            Order covering = findOpenOrderCoveringTable(secondary, restaurantId);
            if (covering != null) {
                String coveringPrimary = canonicalizeTable(covering.getTableNumber());
                if (coveringPrimary != null
                        && !coveringPrimary.equalsIgnoreCase(primary)
                        && !secondaries.contains(coveringPrimary)) {
                    throw new IllegalArgumentException(
                            "La Mesa " + secondary + " ya está unida a Mesa " + coveringPrimary + "."
                    );
                }
            }
        }

        Order primaryOrder = findOpenOrderCoveringTable(primary, restaurantId);
        List<Order> secondaryOrders = new ArrayList<>();
        Set<Long> seenSecondaryOrderIds = new LinkedHashSet<>();
        for (String secondary : secondaries) {
            Order so = findOpenOrderCoveringTable(secondary, restaurantId);
            if (so != null
                    && (primaryOrder == null || !so.getId().equals(primaryOrder.getId()))
                    && seenSecondaryOrderIds.add(so.getId())) {
                secondaryOrders.add(so);
            }
        }

        if (primaryOrder == null && secondaryOrders.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ninguna de las mesas tiene cuenta abierta. Abre primero un pedido en la mesa principal."
            );
        }

        if (primaryOrder == null) {
            primaryOrder = secondaryOrders.removeFirst();
            LinkedHashSet<String> inherited = new LinkedHashSet<>(parseLinkedTables(primaryOrder.getLinkedTables()));
            String oldPrimary = canonicalizeTable(primaryOrder.getTableNumber());
            if (oldPrimary != null && !oldPrimary.equalsIgnoreCase(primary)) {
                inherited.add(oldPrimary);
            }
            primaryOrder.setTableNumber(primary);
            primaryOrder.setLinkedTables(encodeLinkedTables(inherited));
        }

        for (Order secondaryOrder : secondaryOrders) {
            if (secondaryOrder.getId().equals(primaryOrder.getId())) {
                continue;
            }
            absorbOrderDetails(primaryOrder, secondaryOrder);
            LinkedHashSet<String> absorbedLinks = new LinkedHashSet<>(parseLinkedTables(secondaryOrder.getLinkedTables()));
            String secTable = canonicalizeTable(secondaryOrder.getTableNumber());
            if (secTable != null) {
                absorbedLinks.add(secTable);
            }
            LinkedHashSet<String> merged = new LinkedHashSet<>(parseLinkedTables(primaryOrder.getLinkedTables()));
            merged.addAll(absorbedLinks);
            merged.removeIf(t -> t.equalsIgnoreCase(primary));
            primaryOrder.setLinkedTables(encodeLinkedTables(merged));

            secondaryOrder.setStatus(OrderStatus.CLOSED);
            secondaryOrder.setLinkedTables(null);
            for (OrderDetail detail : secondaryOrder.getDetails()) {
                if (detail.getStatus() != OrderItemStatus.DELIVERED) {
                    detail.setStatus(OrderItemStatus.DELIVERED);
                }
            }
            orderRepository.save(secondaryOrder);
            publishOrderEvents(restaurant, mapToResponse(secondaryOrder));
        }

        LinkedHashSet<String> links = new LinkedHashSet<>(parseLinkedTables(primaryOrder.getLinkedTables()));
        links.addAll(secondaries);
        links.removeIf(t -> t.equalsIgnoreCase(primary));
        primaryOrder.setLinkedTables(encodeLinkedTables(links));
        primaryOrder.setTableNumber(primary);

        Order saved = orderRepository.save(primaryOrder);
        log.info(
                "Mesas unidas: primaria={}, vinculadas={}, orden={}",
                primary,
                saved.getLinkedTables(),
                saved.getUuid()
        );

        OrderResponse response = mapToResponse(saved);
        publishOrderEvents(restaurant, response);
        return response;
    }

    private OrderResponse createFreshOrder(
            OrderRequest request,
            Long restaurantId,
            Restaurant restaurant,
            StaffUserDetails staff
    ) {
        Order.OrderBuilder builder = Order.builder()
                .restaurant(restaurant)
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .orderType(request.getOrderType())
                .tableNumber(canonicalizeTable(request.getTableNumber()))
                .deliveryAddress(request.getDeliveryAddress())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .details(new ArrayList<>());
        if (staff != null) {
            builder.staffId(staff.getStaffId()).staffName(staff.getName());
        }
        Order order = builder.build();

        BigDecimal calculatedTotal = addDetailsToOrder(order, request.getDetails(), restaurantId, 1);
        order.setTotalAmount(calculatedTotal);

        Order savedOrder = orderRepository.save(order);
        log.info(
                "Pedido creado: UUID={}, mesa={}, staff={}, total={}",
                savedOrder.getUuid(),
                savedOrder.getTableNumber(),
                savedOrder.getStaffName(),
                savedOrder.getTotalAmount()
        );

        OrderResponse response = mapToResponse(savedOrder);
        publishOrderEvents(restaurant, response);
        return response;
    }

    /** Copia líneas de {@code source} a {@code target} (no mueve entidades: orphanRemoval). */
    private void absorbOrderDetails(Order target, Order source) {
        int nextBatch = target.getDetails().stream()
                .mapToInt(OrderDetail::getBatchNumber)
                .max()
                .orElse(0) + 1;
        int sourceMinBatch = source.getDetails().stream()
                .mapToInt(OrderDetail::getBatchNumber)
                .min()
                .orElse(1);
        int batchOffset = nextBatch - sourceMinBatch;

        for (OrderDetail detail : List.copyOf(source.getDetails())) {
            OrderDetail copy = OrderDetail.builder()
                    .product(detail.getProduct())
                    .quantity(detail.getQuantity())
                    .unitPrice(detail.getUnitPrice())
                    .notes(detail.getNotes())
                    .batchNumber(detail.getBatchNumber() + Math.max(0, batchOffset))
                    .status(detail.getStatus() != null ? detail.getStatus() : OrderItemStatus.PENDING)
                    .build();
            if (detail.getModifiers() != null) {
                for (OrderDetailModifier mod : detail.getModifiers()) {
                    copy.addModifier(OrderDetailModifier.builder()
                            .modifierUuid(mod.getModifierUuid())
                            .name(mod.getName())
                            .priceDelta(mod.getPriceDelta())
                            .build());
                }
            }
            target.addDetail(copy);
        }
        target.setTotalAmount(target.getTotalAmount().add(
                source.getTotalAmount() != null ? source.getTotalAmount() : BigDecimal.ZERO
        ));

        if (target.getStatus() == OrderStatus.ACCEPTED
                || target.getStatus() == OrderStatus.IN_KITCHEN
                || target.getStatus() == OrderStatus.DELIVERED) {
            target.setStatus(OrderStatus.PENDING);
        }
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
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("El restaurante asociado no existe."));
        SubscriptionPlan plan = restaurant.getPlan() != null
                ? restaurant.getPlan()
                : SubscriptionPlan.BASIC;
        Set<UUID> publicProductUuids = null;
        if (plan != SubscriptionPlan.PRO) {
            List<Product> visible = PlanLimits.limitPublicCatalog(
                    plan,
                    productRepository.findByIsAvailableTrue()
            );
            publicProductUuids = visible.stream()
                    .map(Product::getUuid)
                    .collect(Collectors.toSet());
        }

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
            if (publicProductUuids != null && !publicProductUuids.contains(product.getUuid())) {
                throw new IllegalArgumentException(
                        "El producto " + product.getName() + " no está disponible en este momento."
                );
            }

            BigDecimal unitPrice = product.getPrice();
            List<ProductModifier> selectedModifiers = resolveSelectedModifiers(
                    product,
                    detailRequest.getModifierUuids()
            );
            for (ProductModifier modifier : selectedModifiers) {
                unitPrice = unitPrice.add(modifier.getPriceDelta());
            }

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

            for (ProductModifier modifier : selectedModifiers) {
                detail.addModifier(OrderDetailModifier.builder()
                        .modifierUuid(modifier.getUuid())
                        .name(modifier.getName())
                        .priceDelta(modifier.getPriceDelta())
                        .build());
            }

            order.addDetail(detail);
        }
        return added;
    }

    /**
     * Valida selección de modificadores: UUIDs del producto, disponibles,
     * y min/max por grupo.
     */
    private List<ProductModifier> resolveSelectedModifiers(Product product, List<UUID> requestedUuids) {
        List<UUID> requested = requestedUuids == null
                ? List.of()
                : requestedUuids.stream().filter(java.util.Objects::nonNull).distinct().toList();

        List<ProductModifierGroup> groups = product.getModifierGroups() == null
                ? List.of()
                : product.getModifierGroups();

        if (requested.isEmpty()) {
            for (ProductModifierGroup group : groups) {
                if (group.getMinSelect() > 0) {
                    throw new IllegalArgumentException(
                            "En \"" + product.getName() + "\" debes elegir "
                                    + group.getMinSelect()
                                    + " opción(es) de \"" + group.getName() + "\"."
                    );
                }
            }
            return List.of();
        }

        List<ProductModifier> found = productModifierRepository.findByUuidInWithGroup(requested);
        if (found.size() != requested.size()) {
            throw new IllegalArgumentException(
                    "Una o más opciones de \"" + product.getName() + "\" no son válidas."
            );
        }

        Map<Long, List<ProductModifier>> byGroup = new java.util.HashMap<>();
        for (ProductModifier modifier : found) {
            if (!modifier.getRestaurant().getId().equals(product.getRestaurant().getId())) {
                throw new IllegalArgumentException(
                        "Una o más opciones de \"" + product.getName() + "\" no son válidas."
                );
            }
            if (modifier.getGroup() == null
                    || modifier.getGroup().getProduct() == null
                    || !modifier.getGroup().getProduct().getId().equals(product.getId())) {
                throw new IllegalArgumentException(
                        "La opción \"" + modifier.getName() + "\" no pertenece a "
                                + product.getName() + "."
                );
            }
            if (!modifier.isAvailable() || modifier.isDeleted()) {
                throw new IllegalArgumentException(
                        "La opción \"" + modifier.getName() + "\" no está disponible."
                );
            }
            byGroup
                    .computeIfAbsent(modifier.getGroup().getId(), ignored -> new ArrayList<>())
                    .add(modifier);
        }

        for (ProductModifierGroup group : groups) {
            int selected = byGroup.getOrDefault(group.getId(), List.of()).size();
            if (selected < group.getMinSelect() || selected > group.getMaxSelect()) {
                throw new IllegalArgumentException(
                        "En \"" + product.getName() + "\" / \"" + group.getName()
                                + "\" elige entre " + group.getMinSelect()
                                + " y " + group.getMaxSelect() + " opción(es)."
                );
            }
        }

        // Rechazar UUIDs de grupos que ya no existen en el producto (cubierto arriba).
        return found;
    }

    /**
     * Busca orden abierta por UUID de sesión.
     * Acepta la mesa primaria o cualquiera de las mesas vinculadas.
     */
    private Order findOpenOrderForAddition(OrderRequest request, Long restaurantId) {
        if (request.getActiveOrderUuid() == null) {
            return null;
        }
        Order byUuid = orderRepository.findByUuidWithDetails(request.getActiveOrderUuid())
                .orElse(null);
        if (byUuid == null
                || !byUuid.getRestaurant().getId().equals(restaurantId)
                || !OPEN_STATUSES.contains(byUuid.getStatus())) {
            return null;
        }
        String requestTable = canonicalizeTable(request.getTableNumber());
        if (requestTable == null || !orderCoversTable(byUuid, requestTable)) {
            throw new IllegalArgumentException(
                    "El pedido activo no corresponde a esta mesa. Escanea el código QR de tu mesa."
            );
        }
        return byUuid;
    }

    private void validateTableAccess(Restaurant restaurant, OrderRequest request) {
        if (request.getOrderType() != OrderType.IN_TABLE) {
            return;
        }
        String table = canonicalizeTable(request.getTableNumber());
        if (table == null) {
            throw new IllegalArgumentException("El número de mesa es requerido.");
        }
        // El token debe ser el de la mesa escaneada (primaria o vinculada).
        tableQrTokenService.requireValid(restaurant, table, request.getTableToken());
    }

    /**
     * Normaliza etiquetas tipo {@code "Mesa 4"} / {@code "4"} al número canónico de mesa.
     */
    static String canonicalizeTable(String tableNumber) {
        String normalized = TableQrTokenService.normalizeTable(tableNumber);
        if (normalized == null) {
            return null;
        }
        String withoutPrefix = MESA_PREFIX.matcher(normalized).replaceFirst("").trim();
        return withoutPrefix.isEmpty() ? null : withoutPrefix;
    }

    private static String normalizeTable(String tableNumber) {
        return canonicalizeTable(tableNumber);
    }

    @Transactional(readOnly = true)
    public ActiveSessionResponse getActiveSessionByTable(String tableNumber, String tableToken) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        String table = canonicalizeTable(tableNumber);
        if (table == null) {
            return ActiveSessionResponse.builder().hasActiveOrder(false).build();
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("El restaurante asociado no existe."));

        // Sin token válido: no revelar si hay cuenta abierta.
        if (!tableQrTokenService.verify(restaurant, table, tableToken)) {
            return ActiveSessionResponse.builder().hasActiveOrder(false).build();
        }

        Order open = findOpenOrderCoveringTable(table, restaurantId);

        if (open == null) {
            return ActiveSessionResponse.builder().hasActiveOrder(false).build();
        }

        return ActiveSessionResponse.builder()
                .hasActiveOrder(true)
                .order(mapToPublicResponse(open))
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

        OrderStatusAuthorization.assertCanCloseOrder();

        order.setStatus(OrderStatus.CLOSED);
        // Al cobrar: desvincula mesas (quedan libres de forma independiente).
        order.setLinkedTables(null);
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

        return mapToPublicResponse(order);
    }

    private static void validateOrderTypeAllowed(Restaurant restaurant, OrderType orderType) {
        if (orderType == null) {
            throw new IllegalArgumentException("El tipo de pedido es requerido.");
        }
        switch (orderType) {
            case IN_TABLE -> {
            }
            case PICKUP -> {
                if (!isPickupDeliveryAllowed(restaurant) || !restaurant.isHasPickup()) {
                    throw new IllegalArgumentException(
                            "Este restaurante no tiene habilitado el módulo de recoger (pickup)."
                    );
                }
            }
            case DELIVERY -> {
                if (!isPickupDeliveryAllowed(restaurant) || !restaurant.isHasDelivery()) {
                    throw new IllegalArgumentException(
                            "Este restaurante no tiene habilitado el módulo de delivery."
                    );
                }
            }
        }
    }

    private static boolean isPickupDeliveryAllowed(Restaurant restaurant) {
        SubscriptionPlan plan = restaurant.getPlan() != null
                ? restaurant.getPlan()
                : SubscriptionPlan.BASIC;
        PaymentStatus paymentStatus = restaurant.getPaymentStatus() != null
                ? restaurant.getPaymentStatus()
                : PaymentStatus.ACTIVE;
        return PlanLimits.canUseProServiceModules(plan, paymentStatus);
    }

    /** A domicilio exige nombre, teléfono y dirección (además del @NotBlank de customerName). */
    private static void validateDeliveryContact(OrderRequest request) {
        if (request.getOrderType() != OrderType.DELIVERY) {
            return;
        }
        if (request.getCustomerPhone() == null || request.getCustomerPhone().isBlank()) {
            throw new IllegalArgumentException(
                    "El teléfono es requerido para pedidos a domicilio."
            );
        }
        if (request.getDeliveryAddress() == null || request.getDeliveryAddress().isBlank()) {
            throw new IllegalArgumentException(
                    "La dirección de entrega es requerida para pedidos a domicilio."
            );
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

        OrderStatusAuthorization.assertCanUpdateStatus(order.getStatus(), status);

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

        OrderResponse publicView = stripPublicPii(response);
        String trackingTopic = "/topic/order/" + publicView.getUuid();
        messagingTemplate.convertAndSend(trackingTopic, publicView);
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderDetailResponse> detailsResponse = order.getDetails().stream()
                .sorted(Comparator
                        .comparingInt(OrderDetail::getBatchNumber)
                        .thenComparing(OrderDetail::getId, Comparator.nullsLast(Long::compareTo)))
                .map(detail -> {
                    Product product = detail.getProduct();
                    List<OrderDetailModifierResponse> modifiers = detail.getModifiers() == null
                            ? List.of()
                            : detail.getModifiers().stream()
                            .map(mod -> OrderDetailModifierResponse.builder()
                                    .modifierUuid(mod.getModifierUuid())
                                    .name(mod.getName())
                                    .priceDelta(mod.getPriceDelta())
                                    .build())
                            .collect(Collectors.toList());
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
                            .modifiers(modifiers)
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
                .linkedTables(parseLinkedTables(order.getLinkedTables()))
                .staffId(order.getStaffId())
                .staffName(order.getStaffName())
                .deliveryAddress(order.getDeliveryAddress())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .details(detailsResponse)
                .build();
    }

    /** Vista pública: sin PII ni notas de ítems (copia; no muta la vista admin/cocina). */
    private OrderResponse mapToPublicResponse(Order order) {
        return stripPublicPii(mapToResponse(order));
    }

    private static OrderResponse stripPublicPii(OrderResponse response) {
        if (response == null) {
            return null;
        }
        List<OrderDetailResponse> publicDetails = response.getDetails() == null
                ? List.of()
                : response.getDetails().stream()
                .map(detail -> OrderDetailResponse.builder()
                        .id(detail.getId())
                        .productUuid(detail.getProductUuid())
                        .productName(detail.getProductName())
                        .quantity(detail.getQuantity())
                        .unitPrice(detail.getUnitPrice())
                        .subtotal(detail.getSubtotal())
                        .notes(null)
                        .batchNumber(detail.getBatchNumber())
                        .status(detail.getStatus())
                        .modifiers(detail.getModifiers() == null ? List.of() : detail.getModifiers())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(response.getId())
                .uuid(response.getUuid())
                .customerName(null)
                .customerPhone(null)
                .orderType(response.getOrderType())
                .tableNumber(response.getTableNumber())
                .linkedTables(response.getLinkedTables() == null ? List.of() : response.getLinkedTables())
                .staffId(null)
                .staffName(null)
                .deliveryAddress(null)
                .status(response.getStatus())
                .totalAmount(response.getTotalAmount())
                .createdAt(response.getCreatedAt())
                .updatedAt(response.getUpdatedAt())
                .details(publicDetails)
                .build();
    }

    /** Orden abierta cuya mesa primaria o vinculada cubre {@code table}. */
    private Order findOpenOrderCoveringTable(String table, Long restaurantId) {
        if (table == null) {
            return null;
        }
        return orderRepository
                .findOpenInTableOrdersWithDetails(OrderType.IN_TABLE, OPEN_STATUSES)
                .stream()
                .filter(o -> o.getRestaurant() != null && o.getRestaurant().getId().equals(restaurantId))
                .filter(o -> orderCoversTable(o, table))
                .max(Comparator.comparing(Order::getCreatedAt))
                .orElse(null);
    }

    private static boolean orderCoversTable(Order order, String table) {
        if (order == null || table == null) {
            return false;
        }
        String primary = canonicalizeTable(order.getTableNumber());
        if (primary != null && primary.equalsIgnoreCase(table)) {
            return true;
        }
        return parseLinkedTables(order.getLinkedTables()).stream()
                .anyMatch(t -> t.equalsIgnoreCase(table));
    }

    static List<String> parseLinkedTables(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(OrderService::canonicalizeTable)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    static String encodeLinkedTables(Set<String> tables) {
        if (tables == null || tables.isEmpty()) {
            return null;
        }
        String encoded = tables.stream()
                .map(OrderService::canonicalizeTable)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(","));
        return encoded.isEmpty() ? null : encoded;
    }

    private static StaffUserDetails currentStaffOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof StaffUserDetails staff) {
            return staff;
        }
        return null;
    }

    /** Si la mesa es numérica, debe estar dentro del {@code tableCount} del restaurante. */
    private static void assertTableInFloor(Restaurant restaurant, String table) {
        if (table == null || !table.chars().allMatch(Character::isDigit)) {
            return;
        }
        int number;
        try {
            number = Integer.parseInt(table);
        } catch (NumberFormatException ex) {
            return;
        }
        int max = RestaurantProfileService.normalizeStoredTableCount(restaurant.getTableCount());
        if (number < 1 || number > max) {
            throw new IllegalArgumentException(
                    "La Mesa " + table + " está fuera del piso configurado (1–" + max + ")."
            );
        }
    }
}
