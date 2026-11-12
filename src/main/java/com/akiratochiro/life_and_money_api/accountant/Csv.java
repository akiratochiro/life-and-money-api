package com.akiratochiro.life_and_money_api.accountant;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Um CSV no formato do Excel brasileiro (decisão 33): separador {@code ;}, vírgula decimal, data
 * dd/mm/aaaa e UTF-8 com BOM, para o Excel ler os acentos. Um campo que começaria com {@code = + - @}
 * ganha um apóstrofo, senão o Excel executaria uma fórmula vinda do nome de um cliente (CSV injection).
 */
final class Csv {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final List<String> lines = new ArrayList<>();

    Csv(String... header) {
        row((Object[]) header);
    }

    /** Uma linha; {@code null} vira campo vazio, números com vírgula, datas em dd/mm/aaaa. */
    Csv row(Object... values) {
        List<String> cells = new ArrayList<>();
        for (Object value : values) {
            cells.add(cell(value));
        }
        lines.add(String.join(";", cells));
        return this;
    }

    byte[] toBytes() {
        byte[] body = (String.join("\r\n", lines) + "\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, result, 0, BOM.length);
        System.arraycopy(body, 0, result, BOM.length, body.length);
        return result;
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal number) {
            return number.toPlainString().replace('.', ',');
        }
        if (value instanceof LocalDate date) {
            return DATE.format(date);
        }
        if (value instanceof Boolean flag) {
            return flag ? "sim" : "não";
        }
        String text = value.toString();
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(";") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
