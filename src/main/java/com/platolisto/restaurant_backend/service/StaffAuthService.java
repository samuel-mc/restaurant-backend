package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.StaffPinLoginRequest;
import com.platolisto.restaurant_backend.dto.StaffPinLoginResponse;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.StaffMember;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.repository.StaffMemberRepository;
import com.platolisto.restaurant_backend.security.JwtService;
import com.platolisto.restaurant_backend.security.StaffUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffAuthService {

    private final RestaurantRepository restaurantRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public StaffPinLoginResponse loginWithPin(StaffPinLoginRequest request) {
        String slug = request.getTenantSlug().trim().toLowerCase();
        Restaurant restaurant = restaurantRepository.findBySubdomainAndIsActiveTrue(slug)
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas."));

        Long previousTenant = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(restaurant.getId());
        try {
            StaffMember member = staffMemberRepository
                    .findByIdAndRestaurantId(request.getStaffId(), restaurant.getId())
                    .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas."));

            if (!member.isActive()) {
                log.warn("Intento de login con staff inactivo {} en tenant {}", member.getId(), slug);
                throw new BadCredentialsException("Credenciales inválidas.");
            }

            if (!passwordEncoder.matches(request.getPin(), member.getPinHash())) {
                log.warn("PIN inválido para staff {} en tenant {}", member.getId(), slug);
                throw new BadCredentialsException("Credenciales inválidas.");
            }

            StaffUserDetails principal = StaffUserDetails.fromMember(member, restaurant.getId());
            String token = jwtService.generateStaffToken(principal);

            return StaffPinLoginResponse.builder()
                    .token(token)
                    .role(member.getRole().name())
                    .staffId(member.getId().toString())
                    .name(member.getName())
                    .build();
        } finally {
            if (previousTenant != null) {
                TenantContext.setCurrentTenant(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }
}
