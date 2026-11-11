package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Os impostos do mês com a empresa do exemplo: aberta em 01/10/2026, Anexo III fixo, uma nota de
 * 10.000 em 10/2026 com ISS retido e pró-labore de 3.000.
 *
 * <p>DAS de 10/2026: primeiro mês, RBT12 = 10.000 × 12 = 120.000, faixa 1 (6%, sem dedução); o ISS
 * (33,5% da alíquota = 2,01%) foi retido e sai do DAS: 10.000 × (6% − 2,01%) = 399,00.
 * DARF de 10/2026: INSS 11% de 3.000 = 330,00; base do IRRF 3.000 − 607,20 = 2.392,80, isenta.
 * Os dois vencem em 20/11/2026, feriado da Consciência Negra: o DAS vai para segunda, 23/11, e o
 * DARF antecipa para quinta, 19/11.
 */
class TaxIntegrationTest extends IntegrationTest {

    static final String SIMPLES = """
            {"name": "Ana Design", "emoji": "🎨", "openingDate": "2026-10-01",
             "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "ANEXO_III_FIXO"}
            """;
    static final String MEI = """
            {"name": "Solo", "emoji": "💼", "openingDate": "2026-10-01",
             "legalNature": "MEI", "taxRegime": "SIMEI", "meiActivity": "SERVICOS"}
            """;
    static final String DAS = "DAS do Simples Nacional";
    static final String DARF = "DARF do pró-labore (INSS e IRRF)";

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    @Test
    void theMonthShowsTheDasAndTheDarfWithTheCalculation() throws Exception {
        Long ana = register("impostos.mes@test.dev");
        Long company = simplesWithRevenueAndProLabore(ana);

        month(ana, company, "2026-11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value(DARF))
                .andExpect(jsonPath("$.items[0].competence").value("2026-10"))
                .andExpect(jsonPath("$.items[0].dueDate").value("2026-11-19"))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].expectedAmount").value(330.00))
                .andExpect(jsonPath("$.items[0].calculation.darf.proLabores[0].inss").value(330.00))
                .andExpect(jsonPath("$.items[0].calculation.darf.proLabores[0].irrf").value(0))
                .andExpect(jsonPath("$.items[0].calculation.darf.proLabores[0].registered").value(true))
                .andExpect(jsonPath("$.items[1].name").value(DAS))
                .andExpect(jsonPath("$.items[1].dueDate").value("2026-11-23"))
                .andExpect(jsonPath("$.items[1].expectedAmount").value(399.00))
                .andExpect(jsonPath("$.items[1].calculation.simples.rbt12").value(120000.00))
                .andExpect(jsonPath("$.items[1].calculation.simples.rbt12Method").value("FIRST_MONTH"))
                .andExpect(jsonPath("$.items[1].calculation.simples.annex").value("III"))
                .andExpect(jsonPath("$.items[1].calculation.simples.bracket").value(1))
                .andExpect(jsonPath("$.items[1].calculation.simples.revenueWithIssWithheld").value(10000.00))
                .andExpect(jsonPath("$.expectedPending").value(729.00))
                .andExpect(jsonPath("$.paidTotal").value(0))
                .andExpect(jsonPath("$.notice").value(TaxService.NOTICE));

        // Outubro, o mês de abertura: a competência de setembro é anterior, nada vence.
        month(ana, company, "2026-10").andExpect(jsonPath("$.items").isEmpty());

        clock.setTo(Instant.parse("2026-11-24T15:00:00Z"));
        month(ana, company, "2026-11")
                .andExpect(jsonPath("$.items[0].status").value("OVERDUE"))
                .andExpect(jsonPath("$.items[1].status").value("OVERDUE"));
    }

    @Test
    void withoutProLaboreThereIsNoDarf() throws Exception {
        Long ana = register("impostos.semdarf@test.dev");
        Long company = company(ana, SIMPLES);

        month(ana, company, "2026-11")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value(DAS))
                .andExpect(jsonPath("$.items[0].expectedAmount").value(0));
    }

    /** O valor pago vira despesa de impostos; o previsto do momento fica guardado; desfazer apaga a despesa. */
    @Test
    void payingCreatesATaxExpenseAndUnpayingRemovesIt() throws Exception {
        Long ana = register("impostos.pagar@test.dev");
        Long company = simplesWithRevenueAndProLabore(ana);
        Long das = obligation(ana, company, "DAS");

        pay(ana, company, das, "2026-10", "{\"amount\": 401.50, \"date\": \"2026-11-10\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payment.paidAmount").value(401.50))
                .andExpect(jsonPath("$.payment.paidDate").value("2026-11-10"))
                .andExpect(jsonPath("$.payment.expectedAtPayment").value(399.00))
                .andExpect(jsonPath("$.payment.difference").value(2.50));
        mockMvc.perform(get("/companies/{id}/expenses", company).param("month", "2026-11").with(asUser(ana)))
                .andExpect(jsonPath("$.items[?(@.name == 'DAS do Simples Nacional · 10/2026')].amount", contains(401.5)));
        month(ana, company, "2026-11")
                .andExpect(jsonPath("$.paidTotal").value(401.50))
                .andExpect(jsonPath("$.expectedPending").value(330.00));

        pay(ana, company, das, "2026-10", "{\"amount\": 401.50, \"date\": \"2026-11-10\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este imposto já está pago."));

        mockMvc.perform(delete("/companies/{id}/taxes/{oid}/occurrences/{c}/payment", company, das, "2026-10").with(asUser(ana)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/companies/{id}/expenses", company).param("month", "2026-11").with(asUser(ana)))
                .andExpect(jsonPath("$.items").isEmpty());
        month(ana, company, "2026-11").andExpect(jsonPath("$.items[1].status").value("PENDING"));
    }

    @Test
    void paymentRules() throws Exception {
        Long ana = register("impostos.regras@test.dev");
        Long company = simplesWithRevenueAndProLabore(ana);
        Long das = obligation(ana, company, "DAS");

        pay(ana, company, das, "2026-10", "{\"amount\": 399, \"date\": \"2026-11-12\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("A data não pode estar no futuro."));
        pay(ana, company, das, "2026-10", "{\"date\": \"2026-11-10\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Informe o valor pago."));
        pay(ana, company, das, "2026-09", "{\"amount\": 399, \"date\": \"2026-11-10\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Esta obrigação não tem vencimento nessa competência."));
        mockMvc.perform(delete("/companies/{id}/taxes/{oid}/occurrences/{c}/payment", company, das, "2026-10").with(asUser(ana)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Este vencimento não está pago."));
    }

    /** Apagar a despesa do imposto na aba Despesas desfaz o pagamento, como nas contas pessoais. */
    @Test
    void deletingTheTaxExpenseReleasesThePayment() throws Exception {
        Long ana = register("impostos.despesa@test.dev");
        Long company = simplesWithRevenueAndProLabore(ana);
        Long darf = obligation(ana, company, "DARF_PREVIDENCIARIO");
        Long expense = JsonPath.parse(pay(ana, company, darf, "2026-10", "{\"amount\": 330, \"date\": \"2026-11-10\"}")
                .andReturn().getResponse().getContentAsString()).read("$.payment.expenseId", Long.class);

        mockMvc.perform(delete("/companies/{id}/expenses/{eid}", company, expense).with(asUser(ana)))
                .andExpect(status().isNoContent());
        month(ana, company, "2026-11")
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].payment").value(nullValue()));
    }

    /**
     * Março de 2027: a DEFIS de 2026, sem valor, com o aviso da revogação; e o DAS de 02/2027, que o
     * app ainda não calcula, sem previsto.
     */
    @Test
    void theDeclarationIsDeliveredAndTheUncalculatedMonthHasNoExpectedValue() throws Exception {
        Long ana = register("impostos.defis@test.dev");
        Long company = company(ana, SIMPLES);
        Long defis = obligation(ana, company, "DEFIS");

        month(ana, company, "2027-03")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[?(@.kind == 'DAS')].expectedAmount", contains(nullValue())))
                .andExpect(jsonPath("$.items[?(@.kind == 'DAS')].note", contains(ExpectedTaxCalculator.NOT_CALCULATED)))
                .andExpect(jsonPath("$.items[?(@.kind == 'DEFIS')].competence", contains("2026-01")))
                .andExpect(jsonPath("$.items[?(@.kind == 'DEFIS')].dueDate", contains("2027-03-31")))
                .andExpect(jsonPath("$.items[?(@.kind == 'DEFIS')].declaration", contains(true)));

        clock.setTo(Instant.parse("2027-03-20T15:00:00Z"));
        pay(ana, company, defis, "2026-01", "{\"date\": \"2027-03-15\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payment.deliveredOn").value("2027-03-15"))
                .andExpect(jsonPath("$.payment.expenseId").value(nullValue()));
        pay(ana, company, defis, "2026-01", "{\"date\": \"2027-03-15\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta declaração já está marcada como entregue."));
    }

    /** MEI: DAS-MEI de serviços = 5% de 1.621 (81,05) + ISS 5,00 = 86,05; DASN-SIMEI em maio. */
    @Test
    void aMeiHasTheDasMeiAndTheDasn() throws Exception {
        Long ana = register("impostos.mei@test.dev");
        Long company = company(ana, MEI);

        month(ana, company, "2026-11")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].kind").value("DAS_MEI"))
                .andExpect(jsonPath("$.items[0].dueDate").value("2026-11-23"))
                .andExpect(jsonPath("$.items[0].expectedAmount").value(86.05))
                .andExpect(jsonPath("$.items[0].calculation.simei.inss").value(81.05));
        month(ana, company, "2027-05")
                .andExpect(jsonPath("$.items[?(@.kind == 'DASN_SIMEI')].dueDate", contains("2027-05-31")));
    }

    @Test
    void customObligationsAndTheRulesOfTheSystemOnes() throws Exception {
        Long ana = register("impostos.personalizada@test.dev");
        Long company = company(ana, SIMPLES);
        Long das = obligation(ana, company, "DAS");

        createObligation(ana, company, """
                {"name": "Taxa de licença", "recurrence": "YEARLY", "dueDay": 15, "dueRule": "POSTPONE",
                 "fixedAmount": 250, "startMonth": "2026-01"}
                """)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Informe o mês do vencimento da obrigação anual."));
        Long tfe = idOf(createObligation(ana, company, """
                {"name": "Taxa de licença", "recurrence": "YEARLY", "dueDay": 15, "dueMonth": 11, "dueRule": "POSTPONE",
                 "fixedAmount": 250, "startMonth": "2026-01"}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.system").value(false)).andReturn());

        // 15/11/2026 é domingo e feriado; segunda, 16, é dia útil.
        month(ana, company, "2026-11")
                .andExpect(jsonPath("$.items[?(@.name == 'Taxa de licença')].dueDate", contains("2026-11-16")))
                .andExpect(jsonPath("$.items[?(@.name == 'Taxa de licença')].expectedAmount", contains(250.0)));
        month(ana, company, "2026-12").andExpect(jsonPath("$.items[?(@.name == 'Taxa de licença')]").isEmpty());

        mockMvc.perform(delete("/companies/{id}/obligations/{oid}", company, das).with(asUser(ana)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("As obrigações do sistema não podem ser excluídas, só desativadas."));
        mockMvc.perform(patch("/companies/{id}/obligations/{oid}", company, das).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        month(ana, company, "2026-11").andExpect(jsonPath("$.items[?(@.kind == 'DAS')]").isEmpty());

        pay(ana, company, tfe, "2026-11", "{\"amount\": 250, \"date\": \"2026-11-10\"}").andExpect(status().isCreated());
        mockMvc.perform(delete("/companies/{id}/obligations/{oid}", company, tfe).with(asUser(ana)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta obrigação tem pagamentos registrados: desative em vez de excluir."));

        Long once = idOf(createObligation(ana, company, """
                {"name": "Alvará", "recurrence": "ONCE", "dueDay": 31, "dueRule": "NONE", "startMonth": "2026-11"}
                """).andReturn());
        month(ana, company, "2026-11")
                .andExpect(jsonPath("$.items[?(@.name == 'Alvará')].dueDate", contains("2026-11-30")))
                .andExpect(jsonPath("$.items[?(@.name == 'Alvará')].note", contains("Sem valor fixo: informe o valor no pagamento.")));
        mockMvc.perform(delete("/companies/{id}/obligations/{oid}", company, once).with(asUser(ana)))
                .andExpect(status().isNoContent());
    }

    /** Faturando 10.000, o Fator R pede 2.800 de pró-labore, e o Anexo III sai mais barato que o V com o mínimo. */
    @Test
    void theCalculatorComparesTheAnnexes() throws Exception {
        Long ana = register("impostos.calculadora@test.dev");
        Long company = company(ana, SIMPLES);

        mockMvc.perform(get("/companies/{id}/pro-labore-calculator", company).param("monthlyRevenue", "10000").with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annexIII.proLabore").value(2800.00))
                .andExpect(jsonPath("$.cheaper").value("III"))
                .andExpect(jsonPath("$.savings").isNumber());
    }

    @Test
    void anotherUsersTaxesAreNotFound() throws Exception {
        Long ana = register("impostos.isolamento.ana@test.dev");
        Long bruno = register("impostos.isolamento.bruno@test.dev");
        Long anas = company(ana, SIMPLES);
        Long brunos = company(bruno, SIMPLES);
        Long anasDas = obligation(ana, anas, "DAS");

        month(bruno, anas, "2026-11").andExpect(status().isNotFound());
        mockMvc.perform(get("/companies/{id}/obligations", anas).with(asUser(bruno))).andExpect(status().isNotFound());
        pay(bruno, brunos, anasDas, "2026-10", "{\"amount\": 1, \"date\": \"2026-11-10\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Obrigação não encontrada."));
        mockMvc.perform(patch("/companies/{id}/obligations/{oid}", brunos, anasDas).with(asUser(bruno))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/companies/{id}/pro-labore-calculator", anas).param("monthlyRevenue", "1").with(asUser(bruno)))
                .andExpect(status().isNotFound());
    }

    // Apoio

    Long simplesWithRevenueAndProLabore(Long userId) throws Exception {
        Long company = company(userId, SIMPLES);
        byte[] xml;
        try (var in = getClass().getResourceAsStream("/nfse/nfse-v101.xml")) {
            xml = in.readAllBytes();
        }
        mockMvc.perform(multipart("/companies/{id}/invoices", company)
                        .file(new MockMultipartFile("file", "nota.xml", "application/xml", xml)).with(asUser(userId)))
                .andExpect(status().isCreated());
        String partners = mockMvc.perform(get("/companies/{id}/partners", company).with(asUser(userId)))
                .andReturn().getResponse().getContentAsString();
        Long partner = JsonPath.parse(partners).read("$[0].id", Long.class);
        mockMvc.perform(post("/companies/{id}/withdrawals", company).with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"partnerId": %d, "kind": "PRO_LABORE", "competenceMonth": "2026-10",
                                 "paymentDate": "2026-10-30", "grossAmount": 3000}
                                """.formatted(partner)))
                .andExpect(status().isCreated());
        return company;
    }

    Long company(Long userId, String body) throws Exception {
        return idOf(mockMvc.perform(post("/companies").with(asUser(userId)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn());
    }

    Long obligation(Long userId, Long companyId, String kind) throws Exception {
        String body = mockMvc.perform(get("/companies/{id}/obligations", companyId).with(asUser(userId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.parse(body).read("$[?(@.kind == '" + kind + "')].id");
        return ids.getFirst().longValue();
    }

    ResultActions month(Long userId, Long companyId, String month) throws Exception {
        return mockMvc.perform(get("/companies/{id}/taxes", companyId).param("month", month).with(asUser(userId)));
    }

    ResultActions pay(Long userId, Long companyId, Long obligationId, String competence, String body) throws Exception {
        return mockMvc.perform(post("/companies/{id}/taxes/{oid}/occurrences/{c}/payment", companyId, obligationId, competence)
                .with(asUser(userId)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    ResultActions createObligation(Long userId, Long companyId, String body) throws Exception {
        return mockMvc.perform(post("/companies/{id}/obligations", companyId).with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
