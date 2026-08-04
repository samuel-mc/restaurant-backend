package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.FeedbackOutcome;
import com.platolisto.restaurant_backend.entity.FeedbackStatus;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminFeedbackResponse {
    private Long id;
    private UUID orderUuid;
    private short stars;
    private String comment;
    private String contact;
    private String reason;
    private FeedbackOutcome outcome;
    private FeedbackStatus status;
    private boolean urgent;
    private boolean requiresManagerAttention;
    private String tableNumber;
    private OffsetDateTime createdAt;
    private OffsetDateTime resolvedAt;
}
