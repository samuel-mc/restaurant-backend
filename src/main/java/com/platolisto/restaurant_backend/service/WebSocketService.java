package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.MenuUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastProductAvailability(String tenantSlug, UUID productId, boolean isAvailable, String categoryId) {
        if (tenantSlug == null || tenantSlug.isBlank()) {
            log.warn("Cannot broadcast product availability update: tenantSlug is blank");
            return;
        }

        MenuUpdateEvent event = MenuUpdateEvent.builder()
                .type("PRODUCT_AVAILABILITY_CHANGED")
                .productId(productId.toString())
                .isAvailable(isAvailable)
                .categoryId(categoryId)
                .build();

        String topic = "/topic/" + tenantSlug.trim() + "/menu-updates";
        log.info("Broadcasting product availability to topic {}: {}", topic, event);
        messagingTemplate.convertAndSend(topic, event);
    }
}
