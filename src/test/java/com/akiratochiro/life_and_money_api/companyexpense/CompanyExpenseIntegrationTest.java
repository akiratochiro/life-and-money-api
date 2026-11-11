package com.akiratochiro.life_and_money_api.companyexpense;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.akiratochiro.life_and_money_api.invoice.TestPdfs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyExpenseIntegrationTest extends IntegrationTest {

    private final ExpenseCategoryIntegrationTest helper = new ExpenseCategoryIntegrationTest();

    @BeforeEach
    void fixToday() {
        // 11/11/2026, quarta-feira.
        clock.setTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    @Test
    void aOneOffExpenseIsAlreadyPaidAndCannotBeInTheFuture() throws Exception {
        Long ana = register("despesa.avulsa@test.dev");
        Long company = company(ana);
        Long software = category(ana, company, "Software");

        oneOff(ana, company, "Notebook", "4500.00", software, "2026-11-12")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("A data não pode estar no futuro: a despesa registra o que já foi pago."));
        oneOff(ana, company, " Licença IDE ", "89.90", software, "2026-11-03")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Licença IDE"))
                .andExpect(jsonPath("$.hasReceipt").value(false));

        month(ana, company, "2026-11")
                .andExpect(jsonPath("$.paidTotal").value(89.90))
                .andExpect(jsonPath("$.pendingTotal").value(0))
                .andExpect(jsonPath("$.items[0].kind").value("ONE_OFF"))
                .andExpect(jsonPath("$.items[0].status").value("PAID"));
    }

    @Test
    void theTaxesCategoryAndAnArchivedOneCannotBeChosen() throws Exception {
        Long ana = register("despesa.categoria@test.dev");
        Long company = company(ana);
        Long taxes = category(ana, company, "Impostos");
        Long rent = category(ana, company, "Aluguel");
        mockMvc.perform(post("/companies/{id}/expense-categories/{cid}/archive", company, rent).with(asUser(ana)))
                .andExpect(status().isOk());

        oneOff(ana, company, "DAS", "300", taxes, "2026-11-03")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("A categoria Impostos é do sistema")));
        oneOff(ana, company, "Aluguel", "2000", rent, "2026-11-03")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta categoria está arquivada e não aceita novas despesas."));
    }

    @Test
    void aRecurringExpenseShowsEachMonthAsPendingOverdueOrPaid() throws Exception {
        Long ana = register("despesa.fixa@test.dev");
        Long company = company(ana);
        Long rent = recurring(ana, company, "Aluguel", "2000.00", category(ana, company, "Aluguel"), 5, "2026-10", null);

        // Outubro venceu e não foi pago: atrasado. Novembro venceu no dia 5: atrasado também. Dezembro: pendente.
        month(ana, company, "2026-10").andExpect(jsonPath("$.items[0].status").value("OVERDUE"));
        month(ana, company, "2026-12")
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].dueDate").value("2026-12-05"))
                .andExpect(jsonPath("$.pendingTotal").value(2000.00));

        // Paga outubro em novembro, com outro valor: aparece em outubro, que é o mês do vencimento.
        pay(ana, company, rent, "2026-10", "{\"amount\": 2100, \"date\": \"2026-11-03\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(2100))
                .andExpect(jsonPath("$.occurrenceMonth").value("2026-10"));
        month(ana, company, "2026-10")
                .andExpect(jsonPath("$.items[0].status").value("PAID"))
                .andExpect(jsonPath("$.items[0].amount").value(2100))
                .andExpect(jsonPath("$.items[0].expectedAmount").value(2000.00))
                .andExpect(jsonPath("$.items[0].date").value("2026-11-03"))
                .andExpect(jsonPath("$.paidTotal").value(2100));

        pay(ana, company, rent, "2026-10", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este mês já está pago."));
        mockMvc.perform(delete("/companies/{id}/recurring-expenses/{rid}/occurrences/{m}/payment", company, rent, "2026-10")
                        .with(asUser(ana)))
                .andExpect(status().isNoContent());
        month(ana, company, "2026-10").andExpect(jsonPath("$.items[0].status").value("OVERDUE"));
    }

    @Test
    void payingWithoutDetailsUsesTheExpectedAmountAndTheDueDate() throws Exception {
        Long ana = register("despesa.fixa.padrao@test.dev");
        Long company = company(ana);
        Long accountant = recurring(ana, company, "Contador", "450.00", category(ana, company, "Contador"), 10, "2026-11", null);

        pay(ana, company, accountant, "2026-11", null)
                .andExpect(jsonPath("$.amount").value(450.00))
                .andExpect(jsonPath("$.date").value("2026-11-10"))
                .andExpect(jsonPath("$.name").value("Contador"));
        // Dezembro ainda não venceu: sem data, vale hoje.
        pay(ana, company, accountant, "2026-12", null).andExpect(jsonPath("$.date").value("2026-11-11"));
        pay(ana, company, accountant, "2026-09", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Esta despesa fixa não tem vencimento neste mês."));
    }

    @Test
    void editingARecurringExpenseKeepsThePastMonthsAsTheyWere() throws Exception {
        Long ana = register("despesa.fixa.editar@test.dev");
        Long company = company(ana);
        Long rentCategory = category(ana, company, "Aluguel");
        Long rent = recurring(ana, company, "Aluguel", "2000.00", rentCategory, 5, "2026-09", null);

        mockMvc.perform(put("/companies/{id}/recurring-expenses/{rid}", company, rent).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurringJson("Aluguel novo", "2300", rentCategory, 8, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(2300))
                .andExpect(jsonPath("$.startMonth").value("2026-09"));

        month(ana, company, "2026-10")
                .andExpect(jsonPath("$.items[0].name").value("Aluguel"))
                .andExpect(jsonPath("$.items[0].amount").value(2000.00));
        month(ana, company, "2026-11")
                .andExpect(jsonPath("$.items[0].name").value("Aluguel novo"))
                .andExpect(jsonPath("$.items[0].dueDate").value("2026-11-08"));
    }

    @Test
    void deletingARecurringExpenseRemovesItIfNeverPaidOrEndsItIfPaid() throws Exception {
        Long ana = register("despesa.fixa.excluir@test.dev");
        Long company = company(ana);
        Long software = category(ana, company, "Software");
        Long neverPaid = recurring(ana, company, "IDE", "90", software, 5, "2026-09", null);
        Long paid = recurring(ana, company, "Nuvem", "120", software, 5, "2026-09", null);
        pay(ana, company, paid, "2026-09", null).andExpect(status().isCreated());

        mockMvc.perform(delete("/companies/{id}/recurring-expenses/{rid}", company, neverPaid).with(asUser(ana)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/companies/{id}/recurring-expenses/{rid}", company, paid).with(asUser(ana)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/companies/{id}/recurring-expenses", company).with(asUser(ana)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Nuvem"))
                .andExpect(jsonPath("$[0].endMonth").value("2026-10"));
        month(ana, company, "2026-11").andExpect(jsonPath("$.items").isEmpty());
        month(ana, company, "2026-09").andExpect(jsonPath("$.items[0].status").value("PAID"));
    }

    @Test
    void archivingACategoryEndsItsRecurringExpenses() throws Exception {
        Long ana = register("despesa.fixa.arquivar@test.dev");
        Long company = company(ana);
        Long software = category(ana, company, "Software");
        Long cloud = recurring(ana, company, "Nuvem", "120", software, 5, "2026-09", null);
        pay(ana, company, cloud, "2026-09", null).andExpect(status().isCreated());

        mockMvc.perform(post("/companies/{id}/expense-categories/{cid}/archive", company, software).with(asUser(ana)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/companies/{id}/recurring-expenses", company).with(asUser(ana)))
                .andExpect(jsonPath("$[0].endMonth").value("2026-10"));
    }

    @Test
    void aReceiptIsAttachedDownloadedAndRemovedWithTheExpense() throws Exception {
        Long ana = register("despesa.comprovante@test.dev");
        Long company = company(ana);
        Long expense = idOf(oneOff(ana, company, "Cadeira", "800", category(ana, company, "Equipamentos"), "2026-11-02")
                .andReturn());
        byte[] pdf = TestPdfs.minimal();

        receipt(ana, company, expense, new MockMultipartFile("file", "recibo.xml", "application/xml",
                "<x/>".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("O arquivo \"recibo.xml\" não é um PDF, JPEG ou PNG."));
        receipt(ana, company, expense, new MockMultipartFile("file", "recibo.pdf", "application/pdf", pdf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasReceipt").value(true));

        mockMvc.perform(get("/companies/{id}/expenses/{eid}/receipt", company, expense).with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mockMvc.perform(get("/attachments/usage").with(asUser(ana))).andExpect(jsonPath("$.usedBytes").value(pdf.length));

        mockMvc.perform(delete("/companies/{id}/expenses/{eid}", company, expense).with(asUser(ana)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/attachments/usage").with(asUser(ana))).andExpect(jsonPath("$.usedBytes").value(0));
    }

    @Test
    void anArchivedCompanyKeepsItsExpensesReadOnly() throws Exception {
        Long ana = register("despesa.arquivada@test.dev");
        Long company = company(ana);
        Long software = category(ana, company, "Software");
        oneOff(ana, company, "IDE", "90", software, "2026-11-02").andExpect(status().isCreated());
        mockMvc.perform(post("/companies/{id}/archive", company).with(asUser(ana))).andExpect(status().isOk());

        month(ana, company, "2026-11").andExpect(jsonPath("$.items.length()").value(1));
        oneOff(ana, company, "IDE", "90", software, "2026-11-02").andExpect(status().isConflict());
    }

    @Test
    void anotherUsersExpensesAreNotFound() throws Exception {
        Long ana = register("despesa.isolamento.ana@test.dev");
        Long bruno = register("despesa.isolamento.bruno@test.dev");
        Long anas = company(ana);
        Long brunos = company(bruno);
        Long software = category(ana, anas, "Software");
        Long anasExpense = idOf(oneOff(ana, anas, "IDE", "90", software, "2026-11-02").andReturn());
        Long anasRecurring = recurring(ana, anas, "Nuvem", "120", software, 5, "2026-09", null);

        month(bruno, anas, "2026-11").andExpect(status().isNotFound());
        oneOff(bruno, anas, "Intruso", "1", software, "2026-11-02").andExpect(status().isNotFound());
        mockMvc.perform(get("/companies/{id}/recurring-expenses", anas).with(asUser(bruno))).andExpect(status().isNotFound());
        // Pelos caminhos da empresa do Bruno, as despesas da Ana não existem.
        mockMvc.perform(delete("/companies/{id}/expenses/{eid}", brunos, anasExpense).with(asUser(bruno)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Despesa não encontrada."));
        pay(bruno, brunos, anasRecurring, "2026-09", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Despesa fixa não encontrada."));
        oneOff(bruno, brunos, "Com a categoria da Ana", "1", software, "2026-11-02")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Categoria não encontrada."));
    }

    private Long company(Long userId) throws Exception {
        return idOf(mockMvc.perform(post("/companies").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(ExpenseCategoryIntegrationTest.COMPANY))
                .andExpect(status().isCreated()).andReturn());
    }

    private Long category(Long userId, Long companyId, String name) throws Exception {
        String body = mockMvc.perform(get("/companies/{id}/expense-categories", companyId).with(asUser(userId)))
                .andReturn().getResponse().getContentAsString();
        java.util.List<Integer> ids = com.jayway.jsonpath.JsonPath.parse(body).read("$[?(@.name == '" + name + "')].id");
        return ids.getFirst().longValue();
    }

    private ResultActions oneOff(Long userId, Long companyId, String name, String amount, Long categoryId, String date) throws Exception {
        return mockMvc.perform(post("/companies/{id}/expenses", companyId).with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "amount": %s, "categoryId": %d, "date": "%s"}
                        """.formatted(name, amount, categoryId, date)));
    }

    private static String recurringJson(String name, String amount, Long categoryId, int day, String start, String end) {
        return """
                {"name": "%s", "amount": %s, "categoryId": %d, "dueDay": %d, "startMonth": %s, "endMonth": %s}
                """.formatted(name, amount, categoryId, day, start == null ? "null" : "\"" + start + "\"",
                end == null ? "null" : "\"" + end + "\"");
    }

    private Long recurring(Long userId, Long companyId, String name, String amount, Long categoryId, int day,
                           String start, String end) throws Exception {
        return idOf(mockMvc.perform(post("/companies/{id}/recurring-expenses", companyId).with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(recurringJson(name, amount, categoryId, day, start, end)))
                .andExpect(status().isCreated()).andReturn());
    }

    private ResultActions pay(Long userId, Long companyId, Long recurringId, String month, String body) throws Exception {
        var request = post("/companies/{id}/recurring-expenses/{rid}/occurrences/{m}/payment", companyId, recurringId, month)
                .with(asUser(userId));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private ResultActions month(Long userId, Long companyId, String month) throws Exception {
        return mockMvc.perform(get("/companies/{id}/expenses", companyId).param("month", month).with(asUser(userId)));
    }

    private ResultActions receipt(Long userId, Long companyId, Long expenseId, MockMultipartFile file) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(PUT, "/companies/{id}/expenses/{eid}/receipt", companyId, expenseId);
        return mockMvc.perform(request.file(file).with(asUser(userId)));
    }
}
