package com.platolisto.restaurant_backend.multitenancy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    public static void setCurrentTenant(Long tenantId) {
        log.debug("Configurando Tenant ID en ThreadLocal: {}", tenantId);
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        log.debug("Limpiando Tenant ID de ThreadLocal");
        CURRENT_TENANT.remove();
    }
}
