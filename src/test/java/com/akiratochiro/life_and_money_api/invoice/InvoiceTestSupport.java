package com.akiratochiro.life_and_money_api.invoice;

import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

/** Arquivos e corpos usados nos testes de integração das notas. */
final class InvoiceTestSupport {

    /** O CNPJ do prestador na fixture: a empresa dos testes usa o mesmo. */
    static final String PROVIDER_CNPJ = "11222333000181";
    static final String KEY = "35503082212345678000195000000000004226111234567890";
    static final String OTHER_KEY = "35503082212345678000195000000000004326111234567891";

    static final String COMPANY = """
            {"name": "Ana Design", "emoji": "🎨", "cnpj": "%s", "openingDate": "2024-03-10",
             "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "FATOR_R"}
            """;

    private InvoiceTestSupport() {
    }

    static String xml() {
        return NfseXmlParserTest.fixture();
    }

    /** A fixture com outra chave e outro número: uma segunda nota. */
    static String secondXml() {
        return xml().replace("NFS" + KEY, "NFS" + OTHER_KEY).replace("<nNFSe>42</nNFSe>", "<nNFSe>43</nNFSe>");
    }

    static MockMultipartFile file(String part, String filename, String contentType, byte[] content) {
        return new MockMultipartFile(part, filename, contentType, content);
    }

    static MockMultipartFile xmlFile(String part, String xml) {
        return file(part, "nota.xml", "application/xml", xml.getBytes(StandardCharsets.UTF_8));
    }

}
