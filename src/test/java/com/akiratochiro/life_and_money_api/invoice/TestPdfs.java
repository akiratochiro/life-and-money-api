package com.akiratochiro.life_and_money_api.invoice;

import java.nio.charset.StandardCharsets;

/**
 * Um PDF mínimo para os testes. O app reconhece o tipo pelos primeiros bytes ("%PDF-") e nunca lê
 * o conteúdo de um PDF (comprovantes são só guardados), então não precisa de um PDF desenhado.
 */
public final class TestPdfs {

    private TestPdfs() {
    }

    public static byte[] minimal() {
        return "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    }
}
