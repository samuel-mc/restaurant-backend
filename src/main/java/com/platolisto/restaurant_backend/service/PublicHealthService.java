package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.PublicHealthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicHealthService {

    private final JdbcTemplate jdbcTemplate;

    public PublicHealthResponse checkHealth() {
        String dbStatus = "DISCONNECTED";
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result != null && result == 1) {
                dbStatus = "CONNECTED";
            }
        } catch (Exception e) {
            log.error("Database health check failed: {}", e.getMessage());
        }

        return PublicHealthResponse.builder()
                .status("CONNECTED".equals(dbStatus) ? "UP" : "DOWN")
                .database(dbStatus)
                .databaseType("PostgreSQL (Neon)")
                .timestamp(OffsetDateTime.now().toString())
                .build();
    }
}
