package com.platolisto.restaurant_backend.security;

import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Long restaurantId = TenantContext.getCurrentTenant();
        
        Optional<com.platolisto.restaurant_backend.entity.User> userOpt;
        if (restaurantId != null) {
            log.debug("Buscando usuario {} para el restaurantId {}", username, restaurantId);
            userOpt = userRepository.findByRestaurantIdAndEmail(restaurantId, username);
        } else {
            log.debug("Buscando usuario {} de forma global (sin context de restaurant)", username);
            userOpt = userRepository.findByEmail(username);
        }

        com.platolisto.restaurant_backend.entity.User user = userOpt
                .orElseThrow(() -> new UsernameNotFoundException(
                        String.format("Usuario no encontrado con email: %s para el restaurante: %s", username, restaurantId)
                ));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!user.isActive())
                .authorities("ROLE_" + user.getRole().name()) // Spring Security requiere el prefijo ROLE_ para roles
                .build();
    }
}
