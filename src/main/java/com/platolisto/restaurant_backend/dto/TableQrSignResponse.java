package com.platolisto.restaurant_backend.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableQrSignResponse {

    private List<TableQrLink> links;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TableQrLink {
        private String tableNumber;
        private String tableToken;
        /** Caducidad del token (ISO-8601 UTC). */
        private String expiresAt;
    }
}
