package com.akiratochiro.life_and_money_api.accountant;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Os nomes dentro do ZIP (decisão 33): só letras sem acento, números, {@code -} e {@code _}, até 60
 * caracteres, e {@code -2}, {@code -3} quando repetem. Um nome de cliente nunca vira caminho ({@code ../}).
 */
final class FileNames {

    static final int MAX = 60;

    private final Set<String> used = new HashSet<>();

    /** "Cadeira de escritório / Sala 2" → "cadeira-de-escritorio-sala-2"; vazio vira "arquivo". */
    static String slug(String text) {
        String plain = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX) {
            slug = slug.substring(0, MAX).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "arquivo" : slug;
    }

    /** Um nome livre de repetição na mesma pasta, com a extensão dada (sem ponto). */
    String unique(String folder, String base, String extension) {
        String name = slug(base);
        String candidate = folder + name + "." + extension;
        for (int n = 2; !used.add(candidate); n++) {
            candidate = folder + name + "-" + n + "." + extension;
        }
        return candidate;
    }
}
