package com.platolisto.restaurant_backend.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantProfileRequest {

    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String name;

    private String description;

    @Size(max = 7, message = "El color primario debe ser un HEX (#RRGGBB)")
    private String primaryColor;

    @Size(max = 7, message = "El color secundario debe ser un HEX (#RRGGBB)")
    private String secondaryColor;

    @Size(max = 255)
    private String address;

    @Size(max = 512)
    private String googleMapsUrl;

    @Size(max = 30)
    private String whatsapp;

    @Size(max = 255)
    private String businessHours;

    private Boolean hasDelivery;
    private Boolean hasPickup;
    private Boolean hasReservations;
    private Boolean websitePublished;
}
