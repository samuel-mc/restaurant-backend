package com.platolisto.restaurant_backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffPinLoginResponse {

    private String token;
    private String role;
    private String staffId;
    private String name;
}
