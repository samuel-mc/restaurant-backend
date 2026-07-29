package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.StaffMemberRequest;
import com.platolisto.restaurant_backend.dto.StaffMemberResponse;
import com.platolisto.restaurant_backend.dto.StaffMemberUpdateRequest;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.StaffMember;
import com.platolisto.restaurant_backend.exception.TenantNotFoundException;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.repository.StaffMemberRepository;
import com.platolisto.restaurant_backend.security.StaffPinPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffTeamService {

    private final StaffMemberRepository staffMemberRepository;
    private final RestaurantRepository restaurantRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<StaffMemberResponse> listTeam() {
        Long restaurantId = requireTenantId();
        return staffMemberRepository.findByRestaurantIdOrderByNameAsc(restaurantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StaffMemberResponse createMember(StaffMemberRequest request) {
        Long restaurantId = requireTenantId();
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new TenantNotFoundException("Restaurante no encontrado."));

        StaffPinPolicy.requireStrong(request.getPin());
        ensurePinUnique(restaurantId, request.getPin(), null);

        StaffMember member = StaffMember.builder()
                .restaurant(restaurant)
                .name(request.getName().trim())
                .role(request.getRole())
                .pinHash(passwordEncoder.encode(request.getPin()))
                .active(true)
                .build();

        StaffMember saved = staffMemberRepository.save(member);
        log.info("Miembro de equipo creado: {} ({}) en restaurant {}", saved.getName(), saved.getRole(), restaurantId);
        return toResponse(saved);
    }

    @Transactional
    public StaffMemberResponse updateMember(UUID id, StaffMemberUpdateRequest request) {
        Long restaurantId = requireTenantId();
        StaffMember member = staffMemberRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Miembro del equipo no encontrado."));

        if (request.getName() != null && !request.getName().isBlank()) {
            member.setName(request.getName().trim());
        }
        if (request.getRole() != null) {
            member.setRole(request.getRole());
        }
        if (request.getPin() != null && !request.getPin().isBlank()) {
            StaffPinPolicy.requireStrong(request.getPin());
            ensurePinUnique(restaurantId, request.getPin(), id);
            member.setPinHash(passwordEncoder.encode(request.getPin()));
        }
        if (request.getActive() != null) {
            member.setActive(request.getActive());
        }

        return toResponse(staffMemberRepository.save(member));
    }

    @Transactional
    public void deactivateMember(UUID id) {
        Long restaurantId = requireTenantId();
        StaffMember member = staffMemberRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Miembro del equipo no encontrado."));
        member.setActive(false);
        staffMemberRepository.save(member);
    }

    /**
     * Valida unicidad del PIN dentro del tenant (comparando hashes de miembros existentes).
     */
    private void ensurePinUnique(Long restaurantId, String pin, UUID excludeId) {
        List<StaffMember> existing = staffMemberRepository.findByRestaurantIdOrderByNameAsc(restaurantId);
        for (StaffMember member : existing) {
            if (excludeId != null && excludeId.equals(member.getId())) {
                continue;
            }
            if (passwordEncoder.matches(pin, member.getPinHash())) {
                throw new IllegalArgumentException("Ese PIN ya está en uso por otro miembro del equipo.");
            }
        }
    }

    private Long requireTenantId() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo determinar el restaurante actual.");
        }
        return restaurantId;
    }

    private StaffMemberResponse toResponse(StaffMember member) {
        return StaffMemberResponse.builder()
                .id(member.getId())
                .name(member.getName())
                .role(member.getRole())
                .active(member.isActive())
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }
}
