package com.platolisto.restaurant_backend.security;

import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.service.WsTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autentica CONNECT con ticket WS de corta vida y autoriza SUBSCRIBE a topics de cocina/admin.
 * El tracking público {@code /topic/order/{uuid}} permanece anónimo.
 * Los clientes no pueden publicar en el broker ({@code SEND} a {@code /topic} / {@code /queue}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern ADMIN_SLUG_TOPIC =
            Pattern.compile("^/topic/admin/([^/]+)/orders$");
    private static final Pattern ADMIN_FEEDBACK_TOPIC =
            Pattern.compile("^/topic/admin/([^/]+)/feedback$");
    private static final Pattern ADMIN_TABLE_CALLS_TOPIC =
            Pattern.compile("^/topic/admin/([^/]+)/table-calls$");
    private static final Pattern RESTAURANT_ID_TOPIC =
            Pattern.compile("^/topic/restaurants/(\\d+)/orders$");
    private static final Pattern ORDER_TRACKING_TOPIC =
            Pattern.compile("^/topic/order/[0-9a-fA-F-]{36}$");

    private final JwtService jwtService;
    private final WsTicketService wsTicketService;
    private final RestaurantRepository restaurantRepository;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            authenticateConnect(accessor);
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscribe(accessor);
            return message;
        }

        if (StompCommand.SEND.equals(command) || StompCommand.MESSAGE.equals(command)) {
            rejectClientBrokerPublish(accessor);
        }

        return message;
    }

    /**
     * Solo el servidor publica en el simple broker. Un cliente STOMP no debe
     * poder inyectar frames hacia {@code /topic/**} o {@code /queue/**}.
     */
    private void rejectClientBrokerPublish(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Envío WebSocket no permitido.");
        }
        String dest = destination.trim();
        if (dest.startsWith("/topic") || dest.startsWith("/queue")) {
            log.warn("SEND WebSocket denegado hacia broker: {}", dest);
            throw new IllegalArgumentException("No está permitido publicar en este canal.");
        }
        // Prefijo /app es el destino de aplicación; no hay @MessageMapping hoy.
        // Por defensa en profundidad, tampoco aceptamos SEND arbitrario.
        log.warn("SEND WebSocket denegado hacia destino: {}", dest);
        throw new IllegalArgumentException("No está permitido publicar en este canal.");
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String token = extractBearerToken(accessor);
        if (token == null) {
            // Anónimo: solo tracking público.
            return;
        }
        try {
            wsTicketService.authenticateAndConsume(token);
            String subject = jwtService.extractUsername(token);
            Long restaurantId = jwtService.extractRestaurantId(token);
            String role = jwtService.extractRole(token);
            accessor.setUser(new WsAuthenticatedPrincipal(subject, restaurantId, role));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("CONNECT WebSocket con ticket inválido: {}", e.getMessage());
            throw new IllegalArgumentException("No autorizado para WebSocket.");
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Destino de suscripción inválido.");
        }

        if (ORDER_TRACKING_TOPIC.matcher(destination).matches()) {
            return;
        }

        Matcher adminMatcher = ADMIN_SLUG_TOPIC.matcher(destination);
        if (adminMatcher.matches()) {
            requireKitchenAccess(accessor, resolveRestaurantIdBySlug(adminMatcher.group(1)));
            return;
        }

        Matcher feedbackMatcher = ADMIN_FEEDBACK_TOPIC.matcher(destination);
        if (feedbackMatcher.matches()) {
            requireAdminInboxAccess(accessor, resolveRestaurantIdBySlug(feedbackMatcher.group(1)));
            return;
        }

        Matcher tableCallsMatcher = ADMIN_TABLE_CALLS_TOPIC.matcher(destination);
        if (tableCallsMatcher.matches()) {
            // Mesero / admin / cocina autenticados (mismo piso que cocina).
            requireKitchenAccess(accessor, resolveRestaurantIdBySlug(tableCallsMatcher.group(1)));
            return;
        }

        Matcher restaurantMatcher = RESTAURANT_ID_TOPIC.matcher(destination);
        if (restaurantMatcher.matches()) {
            requireKitchenAccess(accessor, Long.parseLong(restaurantMatcher.group(1)));
            return;
        }

        log.warn("Suscripción WebSocket denegada a destino no permitido: {}", destination);
        throw new IllegalArgumentException("Destino de suscripción no permitido.");
    }

    private void requireAdminInboxAccess(StompHeaderAccessor accessor, Long requiredRestaurantId) {
        Principal principal = accessor.getUser();
        if (!(principal instanceof WsAuthenticatedPrincipal wsUser)) {
            throw new IllegalArgumentException("Se requiere autenticación para este canal.");
        }
        if (!wsUser.canAccessAdminInbox()) {
            throw new IllegalArgumentException("Rol no autorizado para el inbox de feedback.");
        }
        if (requiredRestaurantId == null
                || wsUser.restaurantId() == null
                || !requiredRestaurantId.equals(wsUser.restaurantId())) {
            throw new IllegalArgumentException("Acceso denegado a este restaurante.");
        }
    }

    private void requireKitchenAccess(StompHeaderAccessor accessor, Long requiredRestaurantId) {
        Principal principal = accessor.getUser();
        if (!(principal instanceof WsAuthenticatedPrincipal wsUser)) {
            throw new IllegalArgumentException("Se requiere autenticación para este canal.");
        }
        if (!wsUser.canAccessKitchenTopics()) {
            throw new IllegalArgumentException("Rol no autorizado para el canal de cocina.");
        }
        if (requiredRestaurantId == null
                || wsUser.restaurantId() == null
                || !requiredRestaurantId.equals(wsUser.restaurantId())) {
            throw new IllegalArgumentException("Acceso denegado a este restaurante.");
        }
    }

    private Long resolveRestaurantIdBySlug(String slug) {
        return restaurantRepository
                .findBySubdomainAndIsActiveTrue(slug.trim().toLowerCase())
                .map(r -> r.getId())
                .orElseThrow(() -> new IllegalArgumentException("Restaurante no encontrado."));
    }

    private String extractBearerToken(StompHeaderAccessor accessor) {
        String authorization = firstNativeHeader(accessor, "Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        String accessToken = firstNativeHeader(accessor, "access-token");
        if (accessToken != null && !accessToken.isBlank()) {
            return accessToken.trim();
        }
        return null;
    }

    private String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String value = values.getFirst();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
