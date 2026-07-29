package com.platolisto.restaurant_backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WsTicketResponse {
    /** JWT de corta vida solo para STOMP CONNECT. */
    private String ticket;
    /** Segundos hasta la expiración. */
    private long expiresInSeconds;
}
