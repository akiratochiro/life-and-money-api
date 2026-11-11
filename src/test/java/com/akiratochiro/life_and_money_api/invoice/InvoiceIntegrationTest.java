package com.akiratochiro.life_and_money_api.invoice;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;

import static com.akiratochiro.life_and_money_api.invoice.InvoiceTestSupport.*;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvoiceIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    @Test
    void thePreviewReadsTheXmlAndSavesNothing() throws Exception {
        Long ana = register("nota.previa@test.dev");
        Long company = createCompany(ana, PROVIDER_CNPJ);

        preview(ana, company, xmlFile("files", xml()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("nota.xml"))
                .andExpect(jsonPath("$[0].fileType").value("XML"))
                .andExpect(jsonPath("$[0].status").value("READY"))
                .andExpect(jsonPath("$[0].warnings").isEmpty())
                .andExpect(jsonPath("$[0].fields.number").value("42"))
                .andExpect(jsonPath("$[0].fields.competenceDate").value("2026-10-31"))
                .andExpect(jsonPath("$[0].fields.netAmount").value(8450.00))
                .andExpect(jsonPath("$[0].fields.issWithheldAmount").value(200.00));

        list(ana, company, 2026).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/attachments/usage").with(asUser(ana))).andExpect(jsonPath("$.usedBytes").value(0));
    }

    @Test
    void savingAnXmlKeepsTheValuesOfTheFile() throws Exception {
        Long ana = register("nota.salvar@test.dev");
        Long company = createCompany(ana, PROVIDER_CNPJ);

        save(ana, company, xmlFile("file", xml()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("XML"))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.number").value("42"))
                .andExpect(jsonPath("$.accessKey").value(KEY))
                .andExpect(jsonPath("$.issueDate").value("2026-11-10"))
                .andExpect(jsonPath("$.netAmount").value(8450.00))
                .andExpect(jsonPath("$.inssWithheldAmount").value(1100.00))
                .andExpect(jsonPath("$.takerName").value("CLIENTE EXEMPLO S.A."));

        list(ana, company, 2026).andExpect(jsonPath("$[*].number", contains("42")));
        mockMvc.perform(get("/companies/{id}/invoices/years", company).with(asUser(ana)))
                .andExpect(jsonPath("$", contains(2026)));
        mockMvc.perform(get("/attachments/usage").with(asUser(ana)))
                .andExpect(jsonPath("$.usedBytes").value(xml().getBytes(StandardCharsets.UTF_8).length))
                .andExpect(jsonPath("$.limitBytes").value(52428800));
        // Com uma nota, a data de abertura trava.
        mockMvc.perform(get("/companies/{id}", company).with(asUser(ana)))
                .andExpect(jsonPath("$.openingDateEditable").value(false));
    }

    @Test
    void theSameNoteIsMarkedInThePreviewAndRefusedWhenSaved() throws Exception {
        Long ana = register("nota.repetida@test.dev");
        Long company = createCompany(ana, PROVIDER_CNPJ);
        save(ana, company, xmlFile("file", xml())).andExpect(status().isCreated());

        preview(ana, company, xmlFile("files", xml()))
                .andExpect(jsonPath("$[0].status").value("DUPLICATE"))
                .andExpect(jsonPath("$[0].message").value("Você já cadastrou esta nota (nº 42, competência 31/10/2026)."));
        save(ana, company, xmlFile("file", xml()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Você já cadastrou esta nota (nº 42, competência 31/10/2026)."));
    }

    @Test
    void aNoteFromAnotherCnpjIsRefused() throws Exception {
        Long ana = register("nota.outro.cnpj@test.dev");
        Long company = createCompany(ana, "12ABC34501DE35");
        String message = "Esta nota foi emitida pelo CNPJ 11.222.333/0001-81, que não é o desta empresa (12.ABC.345/01DE-35).";

        preview(ana, company, xmlFile("files", xml()))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].message").value(message));
        save(ana, company, xmlFile("file", xml()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(message));
    }

    @Test
    void aCompanyWithoutCnpjAcceptsTheNoteWithAWarning() throws Exception {
        Long ana = register("nota.sem.cnpj@test.dev");
        Long company = createCompany(ana, null);

        preview(ana, company, xmlFile("files", xml()))
                .andExpect(jsonPath("$[0].status").value("READY"))
                .andExpect(jsonPath("$[0].warnings[0]")
                        .value("A empresa não tem CNPJ cadastrado. Esta nota foi emitida pelo CNPJ 11.222.333/0001-81."));
    }

    @Test
    void aHomologationNoteAndANoteBeforeTheOpeningAreRefused() throws Exception {
        Long ana = register("nota.regras@test.dev");
        Long company = createCompany(ana, PROVIDER_CNPJ);

        preview(ana, company, xmlFile("files", xml().replace("<tpAmb>1</tpAmb>", "<tpAmb>2</tpAmb>")))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].message").value(
                        "Esta é uma nota de homologação (ambiente de testes), sem valor fiscal. Envie a nota emitida em produção."));
        save(ana, company, xmlFile("file", xml().replace("<dCompet>2026-10-31</dCompet>", "<dCompet>2024-02-29</dCompet>")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(
                        "A competência desta nota (29/02/2024) é anterior à abertura da empresa (10/03/2024)."));
    }

    @Test
    void aReplacementCancelsTheNoteItReplaces() throws Exception {
        Long ana = register("nota.substituicao@test.dev");
        Long company = createCompany(ana, PROVIDER_CNPJ);
        Long first = idOf(save(ana, company, xmlFile("file", xml())).andReturn());
        String replacement = secondXml().replace("<tpEmit>1</tpEmit>",
                "<tpEmit>1</tpEmit><subst><chSubstda>" + KEY + "</chSubstda><cMotivo>01</cMotivo></subst>");

        preview(ana, company, xmlFile("files", replacement))
                .andExpect(jsonPath("$[0].warnings[0]")
                        .value("Esta nota substitui a nº 42 (competência 31/10/2026), que será marcada como cancelada."));
        save(ana, company, xmlFile("file", replacement))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replacedAccessKey").value(KEY));

        mockMvc.perform(get("/companies/{id}/invoices/{iid}", company, first).with(asUser(ana)))
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    /** Por enquanto só o XML entra: o PDF é reconhecido para a recusa dizer o que fazer. */
    @Test
    void aPdfIsRefusedWithTheGuidanceToSendTheXml() throws Exception {
        Long ana = register("nota.pdf@test.dev");
        Long company = createCompany(ana, PROVIDER_CNPJ);
        String message = "Por enquanto, as notas entram só pelo XML. Baixe o XML da nota no portal da NFS-e "
                + "(o mesmo lugar de onde vem o PDF) e envie esse arquivo.";

        preview(ana, company, file("files", "nota.pdf", "application/pdf", TestPdfs.minimal()))
                .andExpect(jsonPath("$[0].fileType").value("PDF"))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].message").value(message));
        save(ana, company, file("file", "nota.pdf", "application/pdf", TestPdfs.minimal()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(message));
    }

    /** O tipo vem dos primeiros bytes: o nome e o Content-Type mandados pelo navegador não contam. */
    @Test
    void aFileThatIsNotWhatItClaimsIsRefused() throws Exception {
        Long ana = register("nota.falsa@test.dev");
        Long company = createCompany(ana, PROVIDER_CNPJ);

        preview(ana, company,
                file("files", "nota.pdf", "application/pdf", "MZ isto é um executável".getBytes(StandardCharsets.UTF_8)),
                file("files", "vazio.xml", "application/xml", new byte[0]))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].message").value("O arquivo \"nota.pdf\" não é um XML ou PDF."))
                .andExpect(jsonPath("$[1].message").value("O arquivo \"vazio.xml\" está vazio."));
    }

    @Test
    void aFileOver2MegabytesIsRefused() throws Exception {
        Long ana = register("nota.grande@test.dev");
        Long company = createCompany(ana, PROVIDER_CNPJ);
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        Arrays.fill(big, (byte) ' ');
        big[0] = '<';

        preview(ana, company, file("files", "grande.xml", "application/xml", big))
                .andExpect(jsonPath("$[0].message").value("O arquivo \"grande.xml\" tem mais de 2 MB."));
    }

    @Test
    void theQuotaOf50MegabytesPerUserIsEnforced() throws Exception {
        Long ana = register("nota.cota@test.dev");
        Long company = createCompany(ana, PROVIDER_CNPJ);
        // 25 arquivos de 2 MB declarados: a cota soma o tamanho guardado, sem ler os bytes.
        for (int i = 0; i < 25; i++) {
            jdbc.update("insert into attachments (user_id, filename, content_type, size_bytes, sha256, content) "
                    + "values (?, 'antigo.pdf', 'application/pdf', 2097152, repeat('0', 64), '\\x00')", ana);
        }

        save(ana, company, xmlFile("file", xml()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(
                        "Você atingiu o limite de 50 MB de arquivos. Apague notas ou comprovantes antigos para liberar espaço."));
    }

    @Test
    void moreThanTenFilesAtOnceAreRefused() throws Exception {
        Long ana = register("nota.muitos@test.dev");
        Long company = createCompany(ana, PROVIDER_CNPJ);
        MockMultipartFile[] files = new MockMultipartFile[11];
        Arrays.fill(files, xmlFile("files", xml()));

        preview(ana, company, files)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Envie no máximo 10 arquivos por vez."));
    }

    @Test
    void theOriginalIsDownloadedAsAnAttachmentThatTheBrowserWillNotRender() throws Exception {
        Long ana = register("nota.download@test.dev");
        Long company = createCompany(ana, PROVIDER_CNPJ);
        Long invoice = idOf(save(ana, company, file("file", "Nota Outubro.xml", "text/plain",
                xml().getBytes(StandardCharsets.UTF_8))).andReturn());

        mockMvc.perform(get("/companies/{id}/invoices/{iid}/file", company, invoice).with(asUser(ana)))
                .andExpect(status().isOk())
                // O tipo é o reconhecido nos bytes, não o "text/plain" que o navegador mandou.
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/xml"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("Nota Outubro.xml")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(xml().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void cancellingAndReactivatingChangesOnlyTheStatus() throws Exception {
        Long ana = register("nota.status@test.dev");
        Long company = createCompany(ana, PROVIDER_CNPJ);
        Long invoice = idOf(save(ana, company, xmlFile("file", xml())).andReturn());

        mockMvc.perform(post("/companies/{id}/invoices/{iid}/cancel", company, invoice).with(asUser(ana)))
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.netAmount").value(8450.00));
        mockMvc.perform(post("/companies/{id}/invoices/{iid}/reactivate", company, invoice).with(asUser(ana)))
                .andExpect(jsonPath("$.status").value("ATIVA"));
    }

    @Test
    void deletingTheNoteDeletesItsFileAndUnlocksTheOpeningDate() throws Exception {
        Long ana = register("nota.excluir@test.dev");
        Long company = createCompany(ana, PROVIDER_CNPJ);
        Long invoice = idOf(save(ana, company, xmlFile("file", xml())).andReturn());

        mockMvc.perform(delete("/companies/{id}/invoices/{iid}", company, invoice).with(asUser(ana)))
                .andExpect(status().isNoContent());

        list(ana, company, 2026).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/attachments/usage").with(asUser(ana))).andExpect(jsonPath("$.usedBytes").value(0));
        mockMvc.perform(get("/companies/{id}", company).with(asUser(ana)))
                .andExpect(jsonPath("$.openingDateEditable").value(true));
    }

    @Test
    void anArchivedCompanyKeepsItsNotesReadOnly() throws Exception {
        Long ana = register("nota.arquivada@test.dev");
        Long company = createCompany(ana, PROVIDER_CNPJ);
        Long invoice = idOf(save(ana, company, xmlFile("file", xml())).andReturn());
        mockMvc.perform(post("/companies/{id}/archive", company).with(asUser(ana))).andExpect(status().isOk());

        list(ana, company, 2026).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/companies/{id}/invoices/{iid}/file", company, invoice).with(asUser(ana)))
                .andExpect(status().isOk());
        preview(ana, company, xmlFile("files", secondXml())).andExpect(status().isConflict());
        mockMvc.perform(post("/companies/{id}/invoices/{iid}/cancel", company, invoice).with(asUser(ana)))
                .andExpect(status().isConflict());
    }

    @Test
    void aRequestWithoutAFileExplainsWhatIsMissing() throws Exception {
        Long ana = register("nota.sem.arquivo@test.dev");
        Long company = createCompany(ana, PROVIDER_CNPJ);

        mockMvc.perform(multipart("/companies/{id}/invoices", company).with(asUser(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Escolha um arquivo para enviar."));
    }

    Long createCompany(Long userId, String cnpj) throws Exception {
        String body = cnpj == null ? COMPANY.replace("\"cnpj\": \"%s\", ", "") : COMPANY.formatted(cnpj);
        return idOf(mockMvc.perform(post("/companies").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private ResultActions preview(Long userId, Long companyId, MockMultipartFile... files) throws Exception {
        var request = multipart("/companies/{id}/invoices/parse", companyId);
        for (MockMultipartFile file : files) {
            request.file(file);
        }
        return mockMvc.perform(request.with(asUser(userId)));
    }

    ResultActions save(Long userId, Long companyId, MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/companies/{id}/invoices", companyId).file(file).with(asUser(userId)));
    }

    private ResultActions list(Long userId, Long companyId, int year) throws Exception {
        return mockMvc.perform(get("/companies/{id}/invoices", companyId).param("year", String.valueOf(year)).with(asUser(userId)));
    }

}
