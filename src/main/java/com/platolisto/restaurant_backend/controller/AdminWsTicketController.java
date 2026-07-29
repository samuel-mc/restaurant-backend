package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.WsTicketResponse;
import com.platolisto.restaurant_backend.service.WsTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminWsTicketController {

    private final WsTicketService wsTicketService;

    /**
     * Emite un ticket STOMP de corta vida (un solo CONNECT).
     * Autenticación: JWT de sesión en {@code Authorization} (no se expone al browser vía este body).
     */
    @PostMapping("/ws-ticket")
    public ResponseEntity<WsTicketResponse> issueTicket() {
        return ResponseEntity.ok(wsTicketService.issueForCurrentUser());
    }
}
