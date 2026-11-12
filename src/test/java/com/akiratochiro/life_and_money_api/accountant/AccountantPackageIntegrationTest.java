package com.akiratochiro.life_and_money_api.accountant;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.akiratochiro.life_and_money_api.invoice.TestPdfs;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O pacote do contador (decisão 33). Outubro de 2026 da empresa da visão geral (decisão 27): a nota de
 * 10.000 e uma segunda, cancelada; a "Cadeira de escritório" de 200 com comprovante; a fixa "Nuvem" de 120
 * paga; o pró-labore de 3.000; o DAS de outubro pago em 10/11. Hoje é 12/11/2026.
 */
class AccountantPackageIntegrationTest extends IntegrationTest {

    static final String KEY = "35503082212345678000195000000000004226111234567890";
    static final String OTHER_KEY = "35503082212345678000195000000000004326111234567891";

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-12T15:00:00Z"));
    }

    @Test
    void theZipHasTheFilesTheCsvsAndTheSummary() throws Exception {
        Long ana = register("contador.pacote@test.dev");
        Long company = example(ana);

        MvcResult result = download(ana, company, "2026-10")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("ana-design_2026-10.zip")))
                .andReturn();
        Map<String, byte[]> zip = unzip(result.getResponse().getContentAsByteArray());

        assertThat(zip.keySet()).containsExactly(
                "ana-design_2026-10/notas/2026-10-31_nota-42_cliente-exemplo-s-a.xml",
                "ana-design_2026-10/notas/canceladas/2026-10-31_nota-43_cliente-exemplo-s-a.xml",
                "ana-design_2026-10/despesas/2026-10-15_cadeira-de-escritorio.pdf",
                "ana-design_2026-10/notas.csv",
                "ana-design_2026-10/despesas.csv",
                "ana-design_2026-10/impostos.csv",
                "ana-design_2026-10/retiradas.csv",
                "ana-design_2026-10/resumo-2026-10.pdf");
        assertThat(new String(zip.get("ana-design_2026-10/despesas/2026-10-15_cadeira-de-escritorio.pdf"), StandardCharsets.US_ASCII))
                .startsWith("%PDF");

        assertThat(csv(zip, "notas.csv")).hasSize(3)
                .element(1).asString()
                .startsWith("42;" + KEY + ";10/11/2026;31/10/2026;CLIENTE EXEMPLO S.A.;")
                .contains(";10000,00;").endsWith(";8450,00;ativa;notas/2026-10-31_nota-42_cliente-exemplo-s-a.xml");
        assertThat(csv(zip, "notas.csv").get(2)).contains(";cancelada;notas/canceladas/");
        assertThat(csv(zip, "despesas.csv")).containsExactly(
                "Data do pagamento;Despesa;Categoria;Valor;Fixa;Comprovante",
                "05/10/2026;Nuvem;Software;120,00;sim;",
                "15/10/2026;Cadeira de escritório;Software;200,00;não;despesas/2026-10-15_cadeira-de-escritorio.pdf");
        assertThat(csv(zip, "impostos.csv")).hasSize(3)
                .anySatisfy(line -> assertThat(line).startsWith("DAS do Simples Nacional;10/2026;23/11/2026;")
                        .contains(";401,50;").endsWith(";10/11/2026;pago"))
                .anySatisfy(line -> assertThat(line).isEqualTo("DARF do pró-labore (INSS e IRRF);10/2026;19/11/2026;330,00;;;;pendente"));
        assertThat(csv(zip, "retiradas.csv")).containsExactly(
                "Data do pagamento;Sócio;Tipo;Competência;Bruto;INSS;IRRF;Líquido",
                "30/10/2026;Teste;pró-labore;10/2026;3000,00;330,00;0,00;2670,00");

        // O resumo traz os números da visão geral de outubro: resultado 10.000 − 320 − 731,50.
        String pdf = new PdfTextExtractor(new PdfReader(zip.get("ana-design_2026-10/resumo-2026-10.pdf"))).getTextFromPage(1);
        assertThat(pdf).contains("Ana Design", "outubro de 2026", "R$ 10.000,00", "R$ 320,00", "R$ 731,50", "R$ 8.948,50",
                "1 cancelada", "Software", "DAS do Simples Nacional", "pró-labore 10/2026");
    }

    @Test
    void anEmptyMonthHasOnlyTheCsvHeadersAndTheSummary() throws Exception {
        Long ana = register("contador.vazio@test.dev");
        Long company = company(ana);

        Map<String, byte[]> zip = unzip(download(ana, company, "2026-10").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());

        assertThat(zip.keySet()).containsExactly("ana-design_2026-10/notas.csv", "ana-design_2026-10/despesas.csv",
                "ana-design_2026-10/impostos.csv", "ana-design_2026-10/retiradas.csv", "ana-design_2026-10/resumo-2026-10.pdf");
        assertThat(csv(zip, "despesas.csv")).hasSize(1);
        assertThat(zip.get("ana-design_2026-10/notas.csv")).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    void theMonthMustBeFromTheOpeningToNow() throws Exception {
        Long ana = register("contador.mes@test.dev");
        Long company = company(ana);

        for (String month : List.of("2026-09", "2026-12")) {
            mockMvc.perform(get("/companies/{id}/accountant-package", company).param("month", month).with(asUser(ana)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("O pacote vai da abertura da empresa (10/2026) até o mês corrente."));
        }
    }

    @Test
    void anArchivedCompanyCanStillDownload() throws Exception {
        Long ana = register("contador.arquivada@test.dev");
        Long company = company(ana);
        mockMvc.perform(post("/companies/{id}/archive", company).with(asUser(ana))).andExpect(status().isOk());

        download(ana, company, "2026-10").andExpect(status().isOk());
    }

    @Test
    void anotherUsersPackageIsNotFound() throws Exception {
        Long ana = register("contador.isolamento.ana@test.dev");
        Long bruno = register("contador.isolamento.bruno@test.dev");
        Long anas = company(ana);

        mockMvc.perform(get("/companies/{id}/accountant-package", anas).param("month", "2026-10").with(asUser(bruno)))
                .andExpect(status().isNotFound());
    }

    // Apoio

    /** O ZIP é escrito em streaming: a resposta é assíncrona, e o conteúdo chega no dispatch. */
    ResultActions download(Long userId, Long companyId, String month) throws Exception {
        MvcResult started = mockMvc.perform(get("/companies/{id}/accountant-package", companyId).param("month", month)
                        .with(asUser(userId)))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(started));
    }

    static Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    /** As linhas de um CSV do pacote, sem o BOM. */
    static List<String> csv(Map<String, byte[]> zip, String name) {
        byte[] bytes = zip.entrySet().stream().filter(e -> e.getKey().endsWith("/" + name)).findFirst().orElseThrow().getValue();
        return List.of(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8).split("\r\n"));
    }

    Long example(Long userId) throws Exception {
        Long company = company(userId);
        saveXml(userId, company, xml());
        Long cancelled = saveXml(userId, company, xml().replace("NFS" + KEY, "NFS" + OTHER_KEY).replace("<nNFSe>42</nNFSe>", "<nNFSe>43</nNFSe>"));
        mockMvc.perform(post("/companies/{id}/invoices/{iid}/cancel", company, cancelled).with(asUser(userId))).andExpect(status().isOk());

        Long software = categoryId(userId, company, "Software");
        Long chair = idOf(postJson(userId, "/companies/{id}/expenses", company, """
                {"name": "Cadeira de escritório", "amount": 200, "categoryId": %d, "date": "2026-10-15"}
                """.formatted(software)).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(multipart(HttpMethod.PUT, "/companies/{id}/expenses/{eid}/receipt", company, chair)
                        .file(new MockMultipartFile("file", "nota da cadeira.pdf", "application/pdf", TestPdfs.minimal()))
                        .with(asUser(userId)))
                .andExpect(status().isOk());
        Long nuvem = idOf(postJson(userId, "/companies/{id}/recurring-expenses", company, """
                {"name": "Nuvem", "amount": 120, "categoryId": %d, "dueDay": 5, "startMonth": "2026-10"}
                """.formatted(software)).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/companies/{id}/recurring-expenses/{rid}/occurrences/{m}/payment", company, nuvem, "2026-10")
                        .with(asUser(userId)))
                .andExpect(status().isCreated());

        Long partner = JsonPath.parse(mockMvc.perform(get("/companies/{id}/partners", company).with(asUser(userId)))
                .andReturn().getResponse().getContentAsString()).read("$[0].id", Long.class);
        postJson(userId, "/companies/{id}/withdrawals", company, """
                {"partnerId": %d, "kind": "PRO_LABORE", "competenceMonth": "2026-10", "paymentDate": "2026-10-30", "grossAmount": 3000}
                """.formatted(partner)).andExpect(status().isCreated());
        Long das = obligation(userId, company, "DAS");
        mockMvc.perform(post("/companies/{id}/taxes/{oid}/occurrences/{c}/payment", company, das, "2026-10").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 401.50, \"date\": \"2026-11-10\"}"))
                .andExpect(status().isCreated());
        return company;
    }

    Long company(Long userId) throws Exception {
        return idOf(postJson(userId, "/companies", null, """
                {"name": "Ana Design", "emoji": "🎨", "openingDate": "2026-10-01",
                 "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "ANEXO_III_FIXO"}
                """).andExpect(status().isCreated()).andReturn());
    }

    String xml() throws Exception {
        try (var in = getClass().getResourceAsStream("/nfse/nfse-v101.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    Long saveXml(Long userId, Long companyId, String xml) throws Exception {
        return idOf(mockMvc.perform(multipart("/companies/{id}/invoices", companyId)
                        .file(new MockMultipartFile("file", "nota.xml", "application/xml", xml.getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(userId)))
                .andExpect(status().isCreated()).andReturn());
    }

    ResultActions postJson(Long userId, String path, Long companyId, String body) throws Exception {
        var request = companyId == null ? post(path) : post(path, companyId);
        return mockMvc.perform(request.with(asUser(userId)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    Long categoryId(Long userId, Long companyId, String name) throws Exception {
        String body = mockMvc.perform(get("/companies/{id}/expense-categories", companyId).with(asUser(userId)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.parse(body).read("$[?(@.name == '" + name + "')].id");
        return ids.getFirst().longValue();
    }

    Long obligation(Long userId, Long companyId, String kind) throws Exception {
        String body = mockMvc.perform(get("/companies/{id}/obligations", companyId).with(asUser(userId)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.parse(body).read("$[?(@.kind == '" + kind + "')].id");
        return ids.getFirst().longValue();
    }
}
