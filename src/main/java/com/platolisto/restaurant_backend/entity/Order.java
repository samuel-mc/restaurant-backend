package com.platolisto.restaurant_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Filter;

@Entity
@Table(name = "orders")
@Filter(name = "tenantFilter", condition = "restaurant_id = :restaurantId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    @Column(name = "table_number", length = 10)
    private String tableNumber;

    /**
     * Mesas secundarias vinculadas a esta cuenta (coma-separadas, p. ej. {@code 5,6}).
     * La mesa canónica de cobro sigue siendo {@link #tableNumber}.
     */
    @Column(name = "linked_tables", length = 255)
    private String linkedTables;

    /** Mesero que tomó la comanda (login PIN); null si la abrió el comensal por QR. */
    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "staff_name", length = 100)
    private String staffName;

    @Column(name = "delivery_address", columnDefinition = "TEXT")
    private String deliveryAddress;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    /**
     * Método de cobro al cerrar la cuenta.
     * Null mientras la orden está abierta o en cierres históricos previos a V1000.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDetail> details = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID();
        }
    }

    // Helper methods to manage bidirectional relationship
    public void addDetail(OrderDetail detail) {
        details.add(detail);
        detail.setOrder(this);
    }

    public void removeDetail(OrderDetail detail) {
        details.remove(detail);
        detail.setOrder(null);
    }
}
