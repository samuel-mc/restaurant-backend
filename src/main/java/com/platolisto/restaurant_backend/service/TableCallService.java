package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.TableCallRequest;
import com.platolisto.restaurant_backend.dto.TableCallResponse;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.TableCallType;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TableCallService {

    private static final Set<String> PAYMENT_METHODS = Set.of("CASH", "CARD", "TRANSFER");

    private final RestaurantRepository restaurantRepository;
    private final TableQrTokenService tableQrTokenService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public TableCallResponse createCall(TableCallRequest request) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("El restaurante asociado no existe."));

        String table = TableQrTokenService.normalizeTable(request.getTableNumber());
        if (table == null) {
            throw new IllegalArgumentException("El número de mesa es requerido.");
        }

        tableQrTokenService.requireValid(restaurant, table, request.getTableToken());

        TableCallType callType = request.getCallType();
        if (callType == null) {
            throw new IllegalArgumentException("Indica el tipo de llamada.");
        }

        String paymentMethod = null;
        if (callType == TableCallType.BILL) {
            String rawPayment = blankToNull(request.getPaymentMethod());
            paymentMethod = normalizePaymentMethod(request.getPaymentMethod());
            if (rawPayment != null && paymentMethod == null) {
                throw new IllegalArgumentException(
                        "Forma de pago no válida. Usa efectivo, tarjeta o transferencia."
                );
            }
        }

        String note = blankToNull(request.getNote());

        TableCallResponse response = TableCallResponse.builder()
                .eventType(TableCallResponse.EVENT_TYPE)
                .id(UUID.randomUUID())
                .callType(callType)
                .tableNumber(table)
                .paymentMethod(paymentMethod)
                .note(note)
                .createdAt(OffsetDateTime.now())
                .build();

        publish(restaurant, response);
        log.info(
                "TABLE_CALL {} mesa={} restaurantId={}",
                callType,
                table,
                restaurantId
        );
        return response;
    }

    private void publish(Restaurant restaurant, TableCallResponse response) {
        String slug = restaurant.getSubdomain() == null ? null : restaurant.getSubdomain().trim();
        if (slug == null || slug.isEmpty()) {
            return;
        }
        String topic = "/topic/admin/" + slug + "/table-calls";
        messagingTemplate.convertAndSend(topic, response);
    }

    private static String normalizePaymentMethod(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return PAYMENT_METHODS.contains(normalized) ? normalized : null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
