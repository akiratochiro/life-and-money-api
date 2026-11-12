package com.akiratochiro.life_and_money_api.accountant;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTest {

    @Test
    void writesExcelBrazilianCsvWithBom() {
        byte[] bytes = new Csv("Data", "Nome", "Valor", "Fixa")
                .row(LocalDate.of(2026, 10, 15), "Cadeira", new BigDecimal("1234.50"), false)
                .toBytes();

        assertThat(Arrays.copyOf(bytes, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        assertThat(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8))
                .isEqualTo("Data;Nome;Valor;Fixa\r\n15/10/2026;Cadeira;1234,50;não\r\n");
    }

    @Test
    void quotesSeparatorsQuotesAndLineBreaks() {
        assertThat(Csv.cell("Ana; Bia")).isEqualTo("\"Ana; Bia\"");
        assertThat(Csv.cell("O \"melhor\"")).isEqualTo("\"O \"\"melhor\"\"\"");
        assertThat(Csv.cell("duas\nlinhas")).isEqualTo("\"duas\nlinhas\"");
        assertThat(Csv.cell(null)).isEmpty();
    }

    @Test
    void neutralizesFormulas() {
        assertThat(Csv.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(Csv.cell("+55 11")).isEqualTo("'+55 11");
        assertThat(Csv.cell("-10")).isEqualTo("'-10");
        assertThat(Csv.cell("@soma")).isEqualTo("'@soma");
        assertThat(Csv.cell(new BigDecimal("-10.00"))).as("número negativo é número, não fórmula").isEqualTo("-10,00");
    }
}
