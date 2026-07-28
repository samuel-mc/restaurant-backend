package com.platolisto.restaurant_backend.config;

import com.platolisto.restaurant_backend.entity.User;
import com.platolisto.restaurant_backend.entity.UserRole;
import com.platolisto.restaurant_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el SUPER_ADMIN inicial si no existe.
 * Desactivado por defecto; solo el perfil local lo habilita.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final String KNOWN_INSECURE_PASSWORD = "SuperAdmin123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${application.superadmin.email:superadmin@platolisto.com}")
    private String email;

    @Value("${application.superadmin.password:}")
    private String password;

    @Value("${application.superadmin.bootstrap:false}")
    private boolean bootstrap;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bootstrap) {
            return;
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "application.superadmin.bootstrap=true requiere "
                            + "SUPERADMIN_BOOTSTRAP_PASSWORD / application.superadmin.password."
            );
        }
        if (KNOWN_INSECURE_PASSWORD.equals(password)) {
            throw new IllegalStateException(
                    "La contraseña de bootstrap SuperAdmin es insegura y está bloqueada. "
                            + "Define SUPERADMIN_BOOTSTRAP_PASSWORD con un valor único."
            );
        }
        if (userRepository.existsByEmailIgnoreCaseAndRole(email, UserRole.SUPER_ADMIN)) {
            return;
        }

        userRepository.save(User.builder()
                .restaurant(null)
                .name("PlatoListo SuperAdmin")
                .email(email.trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(password))
                .role(UserRole.SUPER_ADMIN)
                .isActive(true)
                .build());

        log.warn(
                "SUPER_ADMIN creado: {} (cámbialo o desactiva bootstrap fuera de local).",
                email
        );
    }
}
