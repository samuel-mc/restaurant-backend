package com.platolisto.restaurant_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Registrar el endpoint para la conexión del cliente (Soporte directo para WebSockets estándar y SockJS)
        registry.addEndpoint("/ws-orders")
                .setAllowedOriginPatterns("*");
        registry.addEndpoint("/ws-orders")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Habilitar un Message Broker simple para enviar notificaciones al cliente en canales con el prefijo /topic
        registry.enableSimpleBroker("/topic");
        
        // Prefijo para los mensajes que se dirigen a los controladores anotados con @MessageMapping
        registry.setApplicationDestinationPrefixes("/app");
    }
}
