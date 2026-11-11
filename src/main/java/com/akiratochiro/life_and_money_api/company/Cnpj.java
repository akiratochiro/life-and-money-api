package com.akiratochiro.life_and_money_api.company;

import java.util.regex.Pattern;

/**
 * Um CNPJ válido, guardado sem pontuação e em maiúsculas.
 *
 * <p>Desde julho de 2026 a Receita emite CNPJs alfanuméricos: as 12 primeiras posições aceitam
 * letras, e os 2 dígitos verificadores continuam numéricos. O cálculo é o mesmo módulo 11 de
 * sempre, com cada caractere valendo o código ASCII menos 48 (dígitos de 0 a 9, "A" = 17 até
 * "Z" = 42). Como um dígito vale ele mesmo, a regra nova também valida os CNPJs numéricos antigos.
 */
public record Cnpj(String value) {

    private static final Pattern FORMAT = Pattern.compile("[0-9A-Z]{12}[0-9]{2}");
    private static final Pattern PUNCTUATION = Pattern.compile("[\\s./-]");
    private static final String FIELD = "cnpj";

    public Cnpj {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw invalid();
        }
        if (value.chars().distinct().count() == 1) {
            // 00.000.000/0000-00 e parecidos passam no módulo 11, mas não são CNPJs.
            throw invalid();
        }
        int first = checkDigit(value.substring(0, 12));
        int second = checkDigit(value.substring(0, 12) + first);
        if (value.charAt(12) - '0' != first || value.charAt(13) - '0' != second) {
            throw invalid();
        }
    }

    /** Aceita com ou sem pontuação e em minúsculas, como o usuário costuma colar. */
    public static Cnpj parse(String raw) {
        if (raw == null) {
            throw invalid();
        }
        return new Cnpj(PUNCTUATION.matcher(raw).replaceAll("").toUpperCase(java.util.Locale.ROOT));
    }

    /** 12.ABC.345/01DE-35 */
    public String formatted() {
        return value.substring(0, 2) + "." + value.substring(2, 5) + "." + value.substring(5, 8)
                + "/" + value.substring(8, 12) + "-" + value.substring(12);
    }

    /** Módulo 11 com pesos de 2 a 9, da direita para a esquerda, recomeçando depois do 9. */
    static int checkDigit(String base) {
        int sum = 0;
        int weight = 2;
        for (int i = base.length() - 1; i >= 0; i--) {
            sum += (base.charAt(i) - '0') * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static InvalidCompanyDataException invalid() {
        return new InvalidCompanyDataException(FIELD, "CNPJ inválido. Confira os números e as letras.");
    }
}
