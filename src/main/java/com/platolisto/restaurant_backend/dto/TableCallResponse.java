package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.TableCallType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento en vivo hacia el tablero de mesero/admin.
 * {@code eventType} fijo {@code TABLE_CALL} para discriminar en el cliente.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableCallResponse {

    public static final String EVENT_TYPE = "TABLE_CALL";

    @Builder.Default
    private String eventType = EVENT_TYPE;

    private UUID id;
    private TableCallType callType;
    private String tableNumber;
    private String paymentMethod;
    private String note;
    private OffsetDateTime createdAt;
}
