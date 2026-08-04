package com.platolisto.restaurant_backend.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CriticalFeedbackAlertEvent {

    @Builder.Default
    private String type = "CRITICAL_FEEDBACK_ALERT";

    private UUID orderUuid;
    private String tableNumber;
    private int stars;
    private List<String> tags;
    private String comment;
    private String timestamp;

    @Builder.Default
    private boolean requiresManagerAttention = true;
}
