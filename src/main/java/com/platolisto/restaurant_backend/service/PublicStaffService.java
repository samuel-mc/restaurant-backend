package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.PublicStaffMemberResponse;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.StaffMember;
import com.platolisto.restaurant_backend.exception.TenantNotFoundException;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.repository.StaffMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicStaffService {

    private final RestaurantRepository restaurantRepository;
    private final StaffMemberRepository staffMemberRepository;

    @Transactional(readOnly = true)
    public List<PublicStaffMemberResponse> listActiveStaff(String tenantSlug) {
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new IllegalArgumentException("El restaurante es requerido.");
        }

        String slug = tenantSlug.trim().toLowerCase();
        Restaurant restaurant = restaurantRepository.findBySubdomainAndIsActiveTrue(slug)
                .orElseThrow(() -> new TenantNotFoundException(
                        "No se encontró un restaurante activo con el subdominio: " + slug));

        Long previousTenant = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(restaurant.getId());
        try {
            return staffMemberRepository.findByRestaurantIdAndActiveTrue(restaurant.getId()).stream()
                    .sorted(Comparator.comparing(StaffMember::getName, String.CASE_INSENSITIVE_ORDER))
                    .map(member -> PublicStaffMemberResponse.builder()
                            .id(member.getId())
                            .name(member.getName())
                            .role(member.getRole())
                            .build())
                    .toList();
        } finally {
            if (previousTenant != null) {
                TenantContext.setCurrentTenant(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }
}
