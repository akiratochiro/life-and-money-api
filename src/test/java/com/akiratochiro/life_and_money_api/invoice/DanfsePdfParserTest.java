package com.akiratochiro.life_and_money_api.invoice;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DanfsePdfParserTest {

    private static final String KEY = "35503082212345678000195000000000004226111234567890";

    @Test
    void theAccessKeyIsFoundHoweverItIsPrinted() {
        assertThat(DanfsePdfParser.accessKey("Chave de Acesso: " + KEY)).isEqualTo(KEY);
        assertThat(DanfsePdfParser.accessKey(KEY.replaceAll("(.{4})", "$1 ").strip())).isEqualTo(KEY);
        assertThat(DanfsePdfParser.accessKey(KEY.replaceAll("(.{8})", "$1."))).isEqualTo(KEY);
    }

    @Test
    void shorterOrLongerRunsOfDigitsAreNotAKey() {
        assertThat(DanfsePdfParser.accessKey("CNPJ 11222333000181 valor 10000")).isNull();
        assertThat(DanfsePdfParser.accessKey(KEY + "1")).isNull();
    }

    @Test
    void aPdfWithTheKeyIsPartlyRecognized() {
        var parsed = DanfsePdfParser.parse(TestPdfs.withLines("DANFSe", "Chave de Acesso da NFS-e", KEY));

        assertThat(parsed).isPresent();
        assertThat(parsed.get().accessKey()).isEqualTo(KEY);
        assertThat(parsed.get().serviceAmount()).isNull();
    }

    /** Um PDF escaneado não tem texto: nada reconhecido, e o usuário preenche à mão. */
    @Test
    void aPdfWithoutTextOrABrokenPdfIsNotRecognized() {
        assertThat(DanfsePdfParser.parse(TestPdfs.withLines())).isEmpty();
        assertThat(DanfsePdfParser.parse("%PDF-1.7 corrompido".getBytes(StandardCharsets.US_ASCII))).isEmpty();
    }
}
