package com.platolisto.restaurant_backend.multitenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldSetAndGetTenantId() {
        // Given
        Long expectedTenantId = 1L;

        // When
        TenantContext.setCurrentTenant(expectedTenantId);

        // Then
        assertThat(TenantContext.getCurrentTenant()).isEqualTo(expectedTenantId);
    }

    @Test
    void shouldClearTenantId() {
        // Given
        TenantContext.setCurrentTenant(1L);

        // When
        TenantContext.clear();

        // Then
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void shouldBeThreadLocalIsolated() throws InterruptedException {
        // Given
        TenantContext.setCurrentTenant(1L);
        AtomicReference<Long> childThreadTenantId = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // When
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                // Should be null in a separate thread
                childThreadTenantId.set(TenantContext.getCurrentTenant());
                
                // Set a value in the child thread
                TenantContext.setCurrentTenant(2L);
                latch.countDown();
            });
        }

        latch.await();

        // Then
        assertThat(TenantContext.getCurrentTenant()).isEqualTo(1L);
        assertThat(childThreadTenantId.get()).isNull();
    }
}
