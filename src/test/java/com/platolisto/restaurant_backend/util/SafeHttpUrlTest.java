package com.platolisto.restaurant_backend.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafeHttpUrlTest {

    @Test
    void acceptsGoogleMapsEmbed() {
        String url = "https://www.google.com/maps/embed?pb=abc";
        assertEquals(url, SafeHttpUrl.requireGoogleMapsUrl(url));
    }

    @Test
    void rejectsEvilMapsHost() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SafeHttpUrl.requireGoogleMapsUrl("https://evil.example/maps/embed")
        );
    }

    @Test
    void acceptsImageOnAllowedHost() {
        String url = "https://cdn.example.com/tenants/a/products/x.jpg";
        assertEquals(url, SafeHttpUrl.requireAllowedImageUrl(url, List.of("cdn.example.com")));
    }

    @Test
    void rejectsImageOnUnknownHost() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SafeHttpUrl.requireAllowedImageUrl(
                        "https://evil.example/img.jpg",
                        List.of("cdn.example.com")
                )
        );
    }

    @Test
    void blankMapsBecomesNull() {
        assertNull(SafeHttpUrl.requireGoogleMapsUrl("  "));
    }
}
