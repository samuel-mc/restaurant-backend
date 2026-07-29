package com.platolisto.restaurant_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hosts permitidos para URLs de imagen externas (CDN propio + extras configurables).
 */
@Component
public class AllowedImageHosts {

    private final List<String> hostSuffixes;

    public AllowedImageHosts(
            @Value("${application.storage.public-base-url:}") String storagePublicBaseUrl,
            @Value("${cloudflare.r2.public-url:}") String r2PublicUrl,
            @Value("${application.security.allowed-image-url-hosts:}") List<String> extraHosts
    ) {
        List<String> hosts = new ArrayList<>();
        addHostFromUrl(hosts, storagePublicBaseUrl);
        addHostFromUrl(hosts, r2PublicUrl);
        if (extraHosts != null) {
            for (String host : extraHosts) {
                if (host != null && !host.isBlank()) {
                    hosts.add(host.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        this.hostSuffixes = List.copyOf(hosts);
    }

    public List<String> hostSuffixes() {
        return hostSuffixes;
    }

    private static void addHostFromUrl(List<String> hosts, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                hosts.add(uri.getHost().toLowerCase(Locale.ROOT));
            }
        } catch (IllegalArgumentException ignored) {
            // Config inválida: se ignora ese origen.
        }
    }
}
