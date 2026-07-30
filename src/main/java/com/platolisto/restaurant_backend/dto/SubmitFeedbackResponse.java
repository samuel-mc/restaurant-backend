package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.FeedbackOutcome;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitFeedbackResponse {
    private FeedbackOutcome outcome;
    /** Solo si outcome = GOOGLE_REVIEW. */
    private String googleMapsUrl;
    private String message;
}
