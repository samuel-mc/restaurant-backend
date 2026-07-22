package com.platolisto.restaurant_backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedeemCouponResponse {
    private String message;
    private String plan;
    private String paymentStatus;
    private boolean websitePublished;
    private String redeemedCode;
}
