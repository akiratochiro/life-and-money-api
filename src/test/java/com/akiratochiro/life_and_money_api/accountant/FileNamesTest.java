package com.akiratochiro.life_and_money_api.accountant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileNamesTest {

    @Test
    void keepsOnlyPlainLettersNumbersAndDashes() {
        assertThat(FileNames.slug("Cadeira de escritório / Sala 2")).isEqualTo("cadeira-de-escritorio-sala-2");
        assertThat(FileNames.slug("CLIENTE EXEMPLO S.A.")).isEqualTo("cliente-exemplo-s-a");
        assertThat(FileNames.slug("2026-10-31_nota-42")).isEqualTo("2026-10-31_nota-42");
    }

    @Test
    void aNameNeverBecomesAPath() {
        assertThat(FileNames.slug("../../etc/passwd")).isEqualTo("etc-passwd");
        assertThat(FileNames.slug("..\\windows")).isEqualTo("windows");
    }

    @Test
    void emptyAndLongNames() {
        assertThat(FileNames.slug("")).isEqualTo("arquivo");
        assertThat(FileNames.slug("§§§")).isEqualTo("arquivo");
        assertThat(FileNames.slug("a".repeat(80))).hasSize(FileNames.MAX);
    }

    @Test
    void repeatedNamesGetASuffixInTheSameFolder() {
        FileNames names = new FileNames();
        assertThat(names.unique("despesas/", "Cadeira", "pdf")).isEqualTo("despesas/cadeira.pdf");
        assertThat(names.unique("despesas/", "cadeira", "pdf")).isEqualTo("despesas/cadeira-2.pdf");
        assertThat(names.unique("despesas/", "Cadeira", "pdf")).isEqualTo("despesas/cadeira-3.pdf");
        assertThat(names.unique("notas/", "cadeira", "pdf")).isEqualTo("notas/cadeira.pdf");
    }
}
