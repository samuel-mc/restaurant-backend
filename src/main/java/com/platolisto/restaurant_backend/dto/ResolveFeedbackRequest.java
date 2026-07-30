package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.FeedbackStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResolveFeedbackRequest {

    @NotNull(message = "Indica el nuevo estado.")
    private FeedbackStatus status;
}
