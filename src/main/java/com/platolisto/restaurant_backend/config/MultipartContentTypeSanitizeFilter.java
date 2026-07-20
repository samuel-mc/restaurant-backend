package com.platolisto.restaurant_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Node/undici (BFF Next.js) envía a veces:
 * {@code multipart/form-data; boundary=…; charset=UTF-8}
 *
 * Ese {@code charset} es inválido en multipart y provoca
 * {@code HttpMediaTypeNotSupportedException} en Spring aunque el controlador
 * declare {@code consumes = "multipart/*"}.
 *
 * Este filtro elimina solo el parámetro charset y conserva el boundary.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MultipartContentTypeSanitizeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String contentType = request.getContentType();
        if (contentType == null || !needsSanitize(contentType)) {
            filterChain.doFilter(request, response);
            return;
        }

        String sanitized = stripCharsetParameter(contentType);
        filterChain.doFilter(new ContentTypeOverrideRequest(request, sanitized), response);
    }

    private static boolean needsSanitize(String contentType) {
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.startsWith("multipart/") && lower.contains("charset=");
    }

    /**
     * Quita {@code charset=…} sin tocar {@code boundary=…}.
     */
    static String stripCharsetParameter(String contentType) {
        StringBuilder out = new StringBuilder();
        String[] parts = contentType.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("charset=")) continue;
            if (!out.isEmpty()) out.append("; ");
            out.append(trimmed);
        }
        return out.toString();
    }

    private static final class ContentTypeOverrideRequest extends HttpServletRequestWrapper {
        private final String contentType;

        private ContentTypeOverrideRequest(HttpServletRequest request, String contentType) {
            super(request);
            this.contentType = contentType;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getHeader(String name) {
            if ("content-type".equalsIgnoreCase(name)) {
                return contentType;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("content-type".equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(contentType));
            }
            return super.getHeaders(name);
        }
    }
}
