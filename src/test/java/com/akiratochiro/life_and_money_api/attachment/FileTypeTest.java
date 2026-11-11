package com.akiratochiro.life_and_money_api.attachment;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypeTest {

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    @Test
    void eachTypeIsRecognizedByItsFirstBytes() {
        assertThat(FileType.detect("%PDF-1.7 ...".getBytes(StandardCharsets.US_ASCII))).contains(FileType.PDF);
        assertThat(FileType.detect(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0))).contains(FileType.PNG);
        assertThat(FileType.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0))).contains(FileType.JPEG);
        assertThat(FileType.detect("<?xml version=\"1.0\"?><NFSe/>".getBytes(StandardCharsets.UTF_8))).contains(FileType.XML);
    }

    @Test
    void anXmlMayStartWithABomAndSpaces() {
        assertThat(FileType.detect(bytes(0xEF, 0xBB, 0xBF, ' ', '\n', '<', 'a', '/', '>'))).contains(FileType.XML);
    }

    /** A extensão e o Content-Type não contam: um texto qualquer chamado nota.pdf não é PDF. */
    @Test
    void anythingElseIsNotRecognized() {
        assertThat(FileType.detect("isto não é uma nota".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(FileType.detect(new byte[0])).isEmpty();
        assertThat(FileType.detect(bytes('M', 'Z', 0x90, 0))).isEmpty();
    }

    @Test
    void theFilenameLosesItsPathAndControlCharacters() {
        assertThat(AttachmentService.cleanName("C:\\fakepath\\nota.xml", FileType.XML)).isEqualTo("nota.xml");
        assertThat(AttachmentService.cleanName("../../etc/nota.pdf", FileType.PDF)).isEqualTo("nota.pdf");
        assertThat(AttachmentService.cleanName("nota\r\nX-Header: 1.xml", FileType.XML)).isEqualTo("notaX-Header: 1.xml");
        assertThat(AttachmentService.cleanName("  ", FileType.PDF)).isEqualTo("arquivo.pdf");
    }

    @Test
    void theRefusalNamesOnlyTheAcceptedTypes() {
        assertThat(AttachmentService.describe(java.util.Set.of(FileType.XML, FileType.PDF))).isEqualTo("um XML ou PDF");
        assertThat(AttachmentService.describe(java.util.Set.of(FileType.PDF, FileType.JPEG, FileType.PNG)))
                .isEqualTo("um PDF, JPEG ou PNG");
        assertThat(AttachmentService.describe(java.util.Set.of(FileType.PDF))).isEqualTo("um PDF");
    }
}
