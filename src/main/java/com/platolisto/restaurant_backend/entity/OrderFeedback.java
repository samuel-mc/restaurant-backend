package com.platolisto.restaurant_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_feedback")
@Filter(name = "tenantFilter", condition = "restaurant_id = :restaurantId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "order_uuid", nullable = false, updatable = false)
    private UUID orderUuid;

    @Column(nullable = false)
    private short stars;

    @Column(name = "comment_text", length = 1000)
    private String comment;

    @Column(length = 120)
    private String contact;

    @Column(length = 40)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FeedbackOutcome outcome;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackStatus status = FeedbackStatus.OPEN;

    @Builder.Default
    @Column(nullable = false)
    private boolean urgent = false;

    @Builder.Default
    @Column(name = "requires_manager_attention", nullable = false)
    private boolean requiresManagerAttention = false;

    @Column(name = "table_number", length = 10)
    private String tableNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
}
