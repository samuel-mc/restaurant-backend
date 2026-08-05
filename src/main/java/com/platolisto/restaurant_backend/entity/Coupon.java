package com.platolisto.restaurant_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(length = 255)
    private String description;

    /** Plan que otorga al canjear (hoy solo PRO). */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "grants_plan", nullable = false, length = 20)
    private SubscriptionPlan grantsPlan = SubscriptionPlan.PRO;

    /** Null = usos ilimitados. */
    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Builder.Default
    @Column(name = "redemption_count", nullable = false)
    private int redemptionCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /** Hasta cuándo se puede canjear el código (no es la renovación del tenant). */
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    /**
     * Días de entitlement al canjear. Null = no fija {@code Restaurant.currentPeriodEnd}.
     * Distinto de {@link #expiresAt}.
     */
    @Column(name = "grant_duration_days")
    private Integer grantDurationDays;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
