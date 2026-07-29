package com.platolisto.restaurant_backend.dto.superadmin;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImpersonateResponse {
    /** Código de un solo uso para canjear la sesión en el subdominio del tenant. */
    private String code;
    private String tenantSlug;
    private String restaurantName;
    private String loginPath;
    /** Segundos hasta que caduca el código de handoff (no el JWT). */
    private long handoffExpiresInSeconds;
    /** Segundos de vida del JWT de soporte una vez canjeado. */
    private long expiresInSeconds;
    /** Email del SuperAdmin que inició la sesión. */
    private String impersonatedBy;
    /** Email del OWNER/ADMIN impersonado. */
    private String impersonatedAs;
}
