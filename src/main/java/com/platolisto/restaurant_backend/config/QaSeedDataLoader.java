package com.platolisto.restaurant_backend.config;

import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.StaffMember;
import com.platolisto.restaurant_backend.entity.StaffRole;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.repository.StaffMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Asegura staff de demo para el tenant {@code latrattoria} en local/QA.
 * No corre en {@code prod}. Nunca loguea PINs en claro.
 */
@Component
@Profile({"local", "qa"})
@RequiredArgsConstructor
@Slf4j
public class QaSeedDataLoader implements ApplicationRunner {

    private final RestaurantRepository restaurantRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        Optional<Restaurant> optional = restaurantRepository.findBySubdomainAndIsActiveTrue("latrattoria");
        if (optional.isEmpty()) {
            return;
        }

        Restaurant restaurant = optional.get();

        updateOrCreateStaff(restaurant, "Admin QA", StaffRole.ADMIN, "1111");
        updateOrCreateStaff(restaurant, "Mesero Carlos", StaffRole.MESERO, "2222");
        updateOrCreateStaff(restaurant, "Chef Luigi", StaffRole.COCINA, "3333");
    }

    private void updateOrCreateStaff(Restaurant restaurant, String name, StaffRole role, String rawPin) {
        List<StaffMember> members = staffMemberRepository.findByRestaurantIdOrderByNameAsc(restaurant.getId());
        Optional<StaffMember> existing = members.stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst();

        if (existing.isPresent()) {
            StaffMember member = existing.get();
            member.setPinHash(passwordEncoder.encode(rawPin));
            staffMemberRepository.save(member);
        } else {
            staffMemberRepository.save(StaffMember.builder()
                    .restaurant(restaurant)
                    .name(name)
                    .role(role)
                    .pinHash(passwordEncoder.encode(rawPin))
                    .active(true)
                    .build());
        }
        log.info("Staff QA asegurado: {} ({})", name, role);
    }
}
