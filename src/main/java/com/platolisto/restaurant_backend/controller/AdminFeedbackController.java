package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.AdminFeedbackResponse;
import com.platolisto.restaurant_backend.dto.FeedbackSummaryResponse;
import com.platolisto.restaurant_backend.dto.ResolveFeedbackRequest;
import com.platolisto.restaurant_backend.entity.FeedbackStatus;
import com.platolisto.restaurant_backend.service.OrderFeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/admin/feedback")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class AdminFeedbackController {

    private final OrderFeedbackService orderFeedbackService;

    @GetMapping("/summary")
    public ResponseEntity<FeedbackSummaryResponse> summary() {
        return ResponseEntity.ok(orderFeedbackService.summary());
    }

    @GetMapping
    public ResponseEntity<List<AdminFeedbackResponse>> list(
            @RequestParam(value = "status", required = false) String statusRaw,
            @RequestParam(value = "urgentOnly", defaultValue = "false") boolean urgentOnly
    ) {
        FeedbackStatus status = parseStatus(statusRaw);
        return ResponseEntity.ok(orderFeedbackService.listForAdmin(status, urgentOnly));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AdminFeedbackResponse> resolve(
            @PathVariable Long id,
            @Valid @RequestBody ResolveFeedbackRequest request
    ) {
        return ResponseEntity.ok(orderFeedbackService.resolve(id, request));
    }

    private static FeedbackStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        try {
            return FeedbackStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Estado de feedback no válido.");
        }
    }
}
