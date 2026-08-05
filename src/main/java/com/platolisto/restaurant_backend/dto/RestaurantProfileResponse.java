package com.platolisto.restaurant_backend.dto;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantProfileResponse {
    private Long id;
    private String name;
    private String subdomain;
    private String logoUrl;
    private String bannerUrl;
    private String faviconUrl;
    private String primaryColor;
    private String secondaryColor;
    private String description;
    private String address;
    private String googleMapsUrl;
    private String whatsapp;
    private String businessHours;
    private boolean hasDelivery;
    private boolean hasPickup;
    private boolean hasReservations;
    private boolean orderingEnabled;
    /** Total de mesas del salón configuradas para el piso. */
    private int tableCount;
    private boolean websitePublished;
    private String plan;
    private String paymentStatus;
    private OffsetDateTime updatedAt;
}
