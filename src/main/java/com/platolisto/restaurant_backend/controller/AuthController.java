package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.LoginRequest;
import com.platolisto.restaurant_backend.dto.LoginResponse;
import com.platolisto.restaurant_backend.security.ClientIpResolver;
import com.platolisto.restaurant_backend.security.JwtDenylistService;
import com.platolisto.restaurant_backend.security.LoginAttemptService;
import com.platolisto.restaurant_backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptService loginAttemptService;
    private final ClientIpResolver clientIpResolver;
    private final JwtDenylistService jwtDenylistService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String accountKey = accountKey(request);
        String ipKey = "auth:ip:" + clientIpResolver.resolve(httpRequest);
        loginAttemptService.assertNotLocked(accountKey, ipKey);
        try {
            LoginResponse response = authService.login(request);
            loginAttemptService.recordSuccess(accountKey, ipKey);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException | UsernameNotFoundException ex) {
            loginAttemptService.recordFailure(accountKey, ipKey);
            throw ex;
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(accountKey, ipKey);
            throw ex;
        }
    }

    /**
     * Invalida el JWT actual (jti en denylist). Requiere Bearer válido.
     * Sin Redis: la denylist vive en memoria de esta instancia.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwtDenylistService.revoke(authHeader.substring(7).trim());
        }
        return ResponseEntity.noContent().build();
    }

    private static String accountKey(LoginRequest request) {
        String email = request.getEmail() != null
                ? request.getEmail().trim().toLowerCase(Locale.ROOT)
                : "unknown";
        return "auth:email:" + email;
    }
}
