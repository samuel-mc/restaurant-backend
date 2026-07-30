package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.AdminFeedbackResponse;
import com.platolisto.restaurant_backend.dto.FeedbackStatusResponse;
import com.platolisto.restaurant_backend.dto.FeedbackSummaryResponse;
import com.platolisto.restaurant_backend.dto.ResolveFeedbackRequest;
import com.platolisto.restaurant_backend.dto.SubmitFeedbackRequest;
import com.platolisto.restaurant_backend.dto.SubmitFeedbackResponse;
import com.platolisto.restaurant_backend.entity.FeedbackOutcome;
import com.platolisto.restaurant_backend.entity.FeedbackStatus;
import com.platolisto.restaurant_backend.entity.Order;
import com.platolisto.restaurant_backend.entity.OrderFeedback;
import com.platolisto.restaurant_backend.entity.OrderStatus;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.exception.ConflictException;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.OrderFeedbackRepository;
import com.platolisto.restaurant_backend.repository.OrderRepository;
import com.platolisto.restaurant_backend.util.SafeHttpUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderFeedbackService {

    private static final Set<String> ALLOWED_REASONS = Set.of("FOOD", "SERVICE", "WAIT", "OTHER");

    private final OrderRepository orderRepository;
    private final OrderFeedbackRepository feedbackRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public FeedbackStatusResponse status(UUID orderUuid) {
        requireTenant();
        return FeedbackStatusResponse.builder()
                .submitted(feedbackRepository.existsByOrderUuid(orderUuid))
                .build();
    }

    @Transactional
    public SubmitFeedbackResponse submit(UUID orderUuid, SubmitFeedbackRequest request) {
        Long restaurantId = requireTenant();
        Order order = orderRepository.findByUuid(orderUuid)
                .orElseThrow(() -> new IllegalArgumentException("No encontramos este pedido."));

        if (order.getRestaurant() == null || !order.getRestaurant().getId().equals(restaurantId)) {
            throw new IllegalArgumentException("No encontramos este pedido.");
        }
        if (order.getStatus() != OrderStatus.CLOSED) {
            throw new IllegalArgumentException(
                    "La evaluación solo está disponible cuando la cuenta ya está cerrada."
            );
        }
        if (feedbackRepository.existsByOrderUuid(orderUuid)) {
            throw new ConflictException("Ya enviaste tu evaluación para este pedido. ¡Gracias!");
        }

        int stars = request.getStars();
        String comment = blankToNull(request.getComment());
        String contact = blankToNull(request.getContact());
        String reason = normalizeReason(request.getReason());

        if (stars <= 3) {
            if (comment == null || comment.length() < 5) {
                throw new IllegalArgumentException(
                        "Cuéntanos qué pasó (al menos unas palabras) para poder ayudarte."
                );
            }
        }

        FeedbackOutcome outcome;
        boolean urgent = false;
        String googleMapsUrl = null;

        if (stars <= 3) {
            outcome = FeedbackOutcome.PRIVATE_COMPLAINT;
            urgent = true;
        } else if (stars == 5) {
            googleMapsUrl = safeMapsUrl(order.getRestaurant());
            outcome = googleMapsUrl != null
                    ? FeedbackOutcome.GOOGLE_REVIEW
                    : FeedbackOutcome.THANKS;
        } else {
            outcome = FeedbackOutcome.THANKS;
        }

        OrderFeedback feedback = OrderFeedback.builder()
                .restaurant(order.getRestaurant())
                .order(order)
                .orderUuid(order.getUuid())
                .stars((short) stars)
                .comment(comment)
                .contact(contact)
                .reason(reason)
                .outcome(outcome)
                .status(FeedbackStatus.OPEN)
                .urgent(urgent)
                .tableNumber(order.getTableNumber())
                .build();

        try {
            feedbackRepository.saveAndFlush(feedback);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Ya enviaste tu evaluación para este pedido. ¡Gracias!");
        }

        if (urgent) {
            publishUrgentAlert(order.getRestaurant(), feedback);
            log.info(
                    "Feedback urgente: restaurantId={}, order={}, stars={}",
                    restaurantId,
                    orderUuid,
                    stars
            );
        }

        return SubmitFeedbackResponse.builder()
                .outcome(outcome)
                .googleMapsUrl(googleMapsUrl)
                .message(messageFor(outcome))
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminFeedbackResponse> listForAdmin(FeedbackStatus status, boolean urgentOnly) {
        Long restaurantId = requireTenant();
        return feedbackRepository.findForAdmin(restaurantId, status, urgentOnly).stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FeedbackSummaryResponse summary() {
        Long restaurantId = requireTenant();
        return FeedbackSummaryResponse.builder()
                .openCount(feedbackRepository.countByRestaurant_IdAndStatus(
                        restaurantId, FeedbackStatus.OPEN))
                .openUrgentCount(feedbackRepository.countByRestaurant_IdAndStatusAndUrgentTrue(
                        restaurantId, FeedbackStatus.OPEN))
                .build();
    }

    @Transactional
    public AdminFeedbackResponse resolve(Long id, ResolveFeedbackRequest request) {
        Long restaurantId = requireTenant();
        OrderFeedback feedback = feedbackRepository.findByIdAndRestaurant_Id(id, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("No encontramos este aviso."));

        FeedbackStatus next = request.getStatus();
        if (next != FeedbackStatus.RESOLVED && next != FeedbackStatus.DISMISSED) {
            throw new IllegalArgumentException("Solo puedes marcar como resuelto o descartado.");
        }
        feedback.setStatus(next);
        feedback.setResolvedAt(OffsetDateTime.now());
        return toAdminResponse(feedbackRepository.save(feedback));
    }

    private void publishUrgentAlert(Restaurant restaurant, OrderFeedback feedback) {
        if (restaurant.getSubdomain() == null || restaurant.getSubdomain().isBlank()) {
            return;
        }
        String topic = "/topic/admin/" + restaurant.getSubdomain().trim() + "/feedback";
        messagingTemplate.convertAndSend(topic, toAdminResponse(feedback));
    }

    private AdminFeedbackResponse toAdminResponse(OrderFeedback f) {
        return AdminFeedbackResponse.builder()
                .id(f.getId())
                .orderUuid(f.getOrderUuid())
                .stars(f.getStars())
                .comment(f.getComment())
                .contact(f.getContact())
                .reason(f.getReason())
                .outcome(f.getOutcome())
                .status(f.getStatus())
                .urgent(f.isUrgent())
                .tableNumber(f.getTableNumber())
                .createdAt(f.getCreatedAt())
                .resolvedAt(f.getResolvedAt())
                .build();
    }

    private static String messageFor(FeedbackOutcome outcome) {
        return switch (outcome) {
            case GOOGLE_REVIEW -> "¡Gracias! Si quieres, deja tu reseña en Google.";
            case PRIVATE_COMPLAINT -> "Gracias por avisarnos. El equipo lo revisará enseguida.";
            case THANKS -> "¡Gracias por tu opinión!";
        };
    }

    private static String safeMapsUrl(Restaurant restaurant) {
        String raw = restaurant.getGoogleMapsUrl();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SafeHttpUrl.requireGoogleMapsUrl(raw);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String normalized = reason.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_REASONS.contains(normalized)) {
            throw new IllegalArgumentException("Motivo no válido.");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Long requireTenant() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante.");
        }
        return restaurantId;
    }
}
