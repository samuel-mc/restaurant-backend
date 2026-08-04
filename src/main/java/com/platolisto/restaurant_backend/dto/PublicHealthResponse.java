package com.platolisto.restaurant_backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicHealthResponse {
    private String status;
    private String database;
    private String databaseType;
    private String timestamp;
}
