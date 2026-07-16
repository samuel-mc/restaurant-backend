package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.LoginRequest;
import com.platolisto.restaurant_backend.dto.LoginResponse;
import com.platolisto.restaurant_backend.entity.User;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.UserRepository;
import com.platolisto.restaurant_backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public LoginResponse login(LoginRequest request) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            log.warn("Intento de login sin contexto de restaurante definido.");
            throw new IllegalArgumentException("No se pudo identificar el restaurante de la solicitud.");
        }

        log.debug("Iniciando autenticación para usuario {} en el restaurante {}", request.getEmail(), restaurantId);

        // 1. Autenticar mediante el AuthenticationManager de Spring Security
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // 2. Recuperar el usuario de base de datos para extraer detalles adicionales
        User user = userRepository.findByRestaurantIdAndEmail(restaurantId, request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado en este restaurante: " + request.getEmail()
                ));

        // 3. Obtener UserDetails para la firma
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        // 4. Generar token firmado con restaurantId y rol
        String jwtToken = jwtService.generateToken(userDetails, user.getRestaurant().getId(), user.getRole().name());

        return LoginResponse.builder()
                .token(jwtToken)
                .build();
    }
}
