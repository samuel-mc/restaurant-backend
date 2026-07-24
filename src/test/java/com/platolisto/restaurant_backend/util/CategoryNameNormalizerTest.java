package com.platolisto.restaurant_backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoryNameNormalizerTest {

    @Test
    void displayNameCapitalizesOnlyFirstLetterOfText() {
        assertEquals(
                "Platillos mexicanos",
                CategoryNameNormalizer.toDisplayName("PLATILLOS MEXICANOS")
        );
        assertEquals(
                "Promoción",
                CategoryNameNormalizer.toDisplayName("  promoción  ")
        );
    }

    @Test
    void matchKeyIgnoresAccentsAndCase() {
        assertEquals(
                CategoryNameNormalizer.toMatchKey("Promoción"),
                CategoryNameNormalizer.toMatchKey("Promocion")
        );
        assertEquals(
                CategoryNameNormalizer.toMatchKey("Entradas"),
                CategoryNameNormalizer.toMatchKey("ENTRADAS")
        );
    }
}
