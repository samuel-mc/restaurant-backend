package com.platolisto.restaurant_backend.util;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validación de URLs http(s) con allowlist de hosts (imágenes, Google Maps).
 */
public final class SafeHttpUrl {

    private static final Set<String> GOOGLE_MAPS_HOSTS = Set.of(
            "www.google.com",
            "maps.google.com",
            "google.com",
            "maps.app.goo.gl",
            "goo.gl"
    );

    private SafeHttpUrl() {}

    /**
     * @return URL normalizada, o {@code null} si {@code raw} es blank
     * @throws IllegalArgumentException si el esquema/host no son seguros
     */
    public static String requireHttpsOrHttp(String raw, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldLabel + " no es una URL válida.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new IllegalArgumentException(
                    fieldLabel + " debe usar http:// o https://."
            );
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(fieldLabel + " no tiene un host válido.");
        }
        return trimmed;
    }

    public static String requireAllowedImageUrl(String raw, List<String> allowedHostSuffixes) {
        String url = requireHttpsOrHttp(raw, "La URL de imagen");
        if (url == null) {
            return null;
        }
        URI uri = URI.create(url);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (isAllowedHost(host, allowedHostSuffixes)) {
            return url;
        }
        throw new IllegalArgumentException(
                "La URL de imagen no está en un dominio permitido. Sube la imagen o usa tu CDN."
        );
    }

    public static String requireGoogleMapsUrl(String raw) {
        String url = requireHttpsOrHttp(raw, "El link de Google Maps");
        if (url == null) {
            return null;
        }
        URI uri = URI.create(url);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        String fullHost = uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = GOOGLE_MAPS_HOSTS.contains(fullHost)
                || GOOGLE_MAPS_HOSTS.contains(host)
                || fullHost.endsWith(".google.com");
        if (!allowed) {
            throw new IllegalArgumentException(
                    "El link de Google Maps debe ser de google.com, maps.app.goo.gl o goo.gl."
            );
        }
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        String query = uri.getQuery() == null ? "" : uri.getQuery().toLowerCase(Locale.ROOT);
        boolean looksLikeMaps = path.contains("/maps")
                || path.contains("/maps/")
                || query.contains("maps")
                || fullHost.contains("maps")
                || fullHost.equals("goo.gl")
                || fullHost.equals("maps.app.goo.gl");
        if (!looksLikeMaps && fullHost.endsWith("google.com") && !path.contains("/maps")) {
            throw new IllegalArgumentException(
                    "El link debe ser una URL de Google Maps (p. ej. maps.google.com o /maps/embed)."
            );
        }
        return url;
    }

    private static boolean isAllowedHost(String host, List<String> allowedHostSuffixes) {
        if (allowedHostSuffixes == null || allowedHostSuffixes.isEmpty()) {
            return false;
        }
        for (String suffix : allowedHostSuffixes) {
            if (suffix == null || suffix.isBlank()) {
                continue;
            }
            String s = suffix.trim().toLowerCase(Locale.ROOT);
            if (s.startsWith(".")) {
                s = s.substring(1);
            }
            if (host.equals(s) || host.endsWith("." + s)) {
                return true;
            }
        }
        return false;
    }
}
