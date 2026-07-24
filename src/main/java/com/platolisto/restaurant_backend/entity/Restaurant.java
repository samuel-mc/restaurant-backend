package com.platolisto.restaurant_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String subdomain;

    @Column(name = "custom_domain", unique = true, length = 100)
    private String customDomain;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Column(name = "banner_url", length = 512)
    private String bannerUrl;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "secondary_color", length = 7)
    private String secondaryColor;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String address;

    @Column(name = "google_maps_url", length = 512)
    private String googleMapsUrl;

    @Column(length = 30)
    private String whatsapp;

    @Column(name = "business_hours", length = 255)
    private String businessHours;

    @Builder.Default
    @Column(name = "has_delivery", nullable = false)
    private boolean hasDelivery = false;

    @Builder.Default
    @Column(name = "has_pickup", nullable = false)
    private boolean hasPickup = true;

    @Builder.Default
    @Column(name = "has_reservations", nullable = false)
    private boolean hasReservations = false;

    /**
     * Si el menú digital acepta pedidos. Si es {@code false}, el menú es solo consulta.
     */
    @Builder.Default
    @Column(name = "ordering_enabled", nullable = false)
    private boolean orderingEnabled = true;

    /** Si el website institucional del tenant está visible al público. */
    @Builder.Default
    @Column(name = "website_published", nullable = false)
    private boolean websitePublished = false;

    /** Plan comercial (BASIC | PRO). Enterprise no se persiste aún. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    private SubscriptionPlan plan = SubscriptionPlan.BASIC;

    /** Cobro: ACTIVE o PENDING_PAYMENT (Pro sin confirmar). */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.ACTIVE;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
