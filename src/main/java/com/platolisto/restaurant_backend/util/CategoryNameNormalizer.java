package com.platolisto.restaurant_backend.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalización de nombres de categoría:
 * - Clave de igualdad: sin acentos, minúsculas, espacios colapsados
 *   (p. ej. "Promoción" ≡ "Promocion").
 * - Título al guardar: primera letra del texto en mayúscula, resto en minúscula
 *   (p. ej. "PLATILLOS MEXICANOS" → "Platillos mexicanos").
 */
public final class CategoryNameNormalizer {

    private static final Locale ES = Locale.forLanguageTag("es-MX");

    private CategoryNameNormalizer() {
    }

    /** Nombre listo para persistir (máx. 50 chars se valida afuera). */
    public static String toDisplayName(String raw) {
        String collapsed = collapseWhitespace(raw);
        if (collapsed.isEmpty()) {
            return collapsed;
        }
        String lower = collapsed.toLowerCase(ES);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** Clave comparable entre variantes con/sin acento y distinta capitalización. */
    public static String toMatchKey(String raw) {
        String collapsed = collapseWhitespace(raw);
        if (collapsed.isEmpty()) {
            return "";
        }
        String nfd = Normalizer.normalize(collapsed, Normalizer.Form.NFD);
        String withoutMarks = nfd.replaceAll("\\p{M}+", "");
        return withoutMarks.toLowerCase(Locale.ROOT);
    }

    private static String collapseWhitespace(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }
}
