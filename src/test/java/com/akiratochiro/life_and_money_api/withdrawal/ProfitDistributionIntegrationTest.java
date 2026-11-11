package com.akiratochiro.life_and_money_api.withdrawal;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Distribuição de lucros (Fase 6): sem INSS, IRRF informado, e os dois avisos que não bloqueiam. Hoje: 11/11/2026. */
class ProfitDistributionIntegrationTest extends IntegrationTest {

    /** Aberta em 01/10/2026, Anexo III fixo: a nota de 10.000 (10/2026) dá o exemplo do limite. */
    static final String SIMPLES = """
            {"name": "Ana Design", "emoji": "🎨", "openingDate": "2026-10-01",
             "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "ANEXO_III_FIXO"}
            """;
    static final String MEI = """
            {"name": "Solo", "emoji": "💼", "openingDate": "2026-10-01",
             "legalNature": "MEI", "taxRegime": "SIMEI", "meiActivity": "SERVICOS"}
            """;
    static final String PRESUMIDO = """
            {"name": "Consultoria", "emoji": "📊", "openingDate": "2026-10-01",
             "legalNature": "LTDA", "taxRegime": "LUCRO_PRESUMIDO"}
            """;

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    /**
     * Limite até 11/2026: outubro 10.000 × 32% − IRPJ do DAS (10.000 × 6% × 4% = 24,00) = 3.176,00; novembro
     * sem receita, zero. 3.000 cabem; mais 500 (3.500 no ano) passam.
     */
    @Test
    void theExemptionLimitIsThePresumptionMinusTheIrpjOfTheDas() throws Exception {
        Long ana = register("lucros.limite@test.dev");
        Long company = withRevenue(ana, SIMPLES);
        Long me = me(ana, company);

        distribute(ana, company, me, "2026-11-05", "3000", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("DISTRIBUICAO_LUCROS"))
                .andExpect(jsonPath("$.competenceMonth").doesNotExist())
                .andExpect(jsonPath("$.inssAmount").value(0))
                .andExpect(jsonPath("$.netAmount").value(3000.00))
                .andExpect(jsonPath("$.warnings").isEmpty());
        distribute(ana, company, me, "2026-11-10", "500", null)
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0]", containsString("(R$ 3.500,00) passam do limite de isenção estimado "
                        + "sem contabilidade (R$ 3.176,00)")));
    }

    /** 30.000 + 25.000 no mesmo mês ao mesmo sócio = 55.000: retenção sugerida de 10% sobre o total, 5.500,00. */
    @Test
    void aboveFiftyThousandInTheMonthTheWithholdingIsSuggested() throws Exception {
        Long ana = register("lucros.50mil@test.dev");
        Long company = withRevenue(ana, SIMPLES);
        Long me = me(ana, company);
        distribute(ana, company, me, "2026-11-02", "30000", null).andExpect(status().isCreated());

        distribute(ana, company, me, "2026-11-09", "25000", "5500")
                .andExpect(jsonPath("$.irrfAmount").value(5500.00))
                .andExpect(jsonPath("$.netAmount").value(19500.00))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("somam R$ 55.000,00, acima de R$ 50.000,00. Pela Lei "
                        + "15.270/2025, há retenção de 10% de IR sobre o total (R$ 5.500,00)"))));
    }

    /** MEI de serviços: 10.000 × 32% = 3.200,00, sem IRPJ a descontar. E o pró-labore continua recusado. */
    @Test
    void aMeiDistributesProfitWithItsOwnPresumption() throws Exception {
        Long ana = register("lucros.mei@test.dev");
        Long company = withRevenue(ana, MEI);
        Long me = me(ana, company);

        distribute(ana, company, me, "2026-11-05", "3200", null).andExpect(jsonPath("$.warnings").isEmpty());
        distribute(ana, company, me, "2026-11-06", "1", null)
                .andExpect(jsonPath("$.warnings[0]", containsString("(R$ 3.201,00) passam do limite")))
                .andExpect(jsonPath("$.warnings[0]", containsString("(R$ 3.200,00)")));
    }

    @Test
    void outsideTheSimplesTheLimitIsLeftToTheAccountant() throws Exception {
        Long ana = register("lucros.presumido@test.dev");
        Long company = company(ana, PRESUMIDO);

        distribute(ana, company, me(ana, company), "2026-11-05", "1000", null)
                .andExpect(jsonPath("$.warnings[0]", containsString("No Lucro Presumido e no Real, o limite de isenção depende")));
    }

    @Test
    void distributionRules() throws Exception {
        Long ana = register("lucros.regras@test.dev");
        Long company = company(ana, SIMPLES);
        Long me = me(ana, company);

        postWithdrawal(ana, company, """
                {"partnerId": %d, "kind": "DISTRIBUICAO_LUCROS", "paymentDate": "2026-11-05", "grossAmount": 1000, "inssAmount": 110}
                """.formatted(me))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("A distribuição de lucros não tem INSS."));
        distribute(ana, company, me, "2026-11-12", "1000", null)
                .andExpect(jsonPath("$.detail").value("A data do pagamento não pode estar no futuro: a retirada registra o que já saiu da conta."));
        distribute(ana, company, me, "2026-09-30", "1000", null)
                .andExpect(jsonPath("$.detail").value("A data do pagamento é anterior à abertura da empresa."));
        distribute(ana, company, me, "2026-11-05", "1000", "1000")
                .andExpect(jsonPath("$.detail").value("O IRRF não pode ser igual ou maior que o valor distribuído."));

        Long id = idOf(distribute(ana, company, me, "2026-11-05", "1000", null).andReturn());
        mockMvc.perform(put("/companies/{id}/withdrawals/{wid}", company, id).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WithdrawalIntegrationTest.body(me, "2026-11", "2026-11-05", "1000")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("O tipo da retirada não muda: exclua e registre de novo."));
        mockMvc.perform(put("/companies/{id}/withdrawals/{wid}", company, id).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(distribution(me, "2026-11-06", "1200", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossAmount").value(1200));
        mockMvc.perform(get("/companies/{id}/withdrawals", company).param("year", "2026").with(asUser(ana)))
                .andExpect(jsonPath("$[0].calculated").value(true));
    }

    // Apoio

    Long withRevenue(Long userId, String body) throws Exception {
        Long company = company(userId, body);
        byte[] xml;
        try (var in = getClass().getResourceAsStream("/nfse/nfse-v101.xml")) {
            xml = in.readAllBytes();
        }
        mockMvc.perform(multipart("/companies/{id}/invoices", company)
                        .file(new MockMultipartFile("file", "nota.xml", "application/xml", xml)).with(asUser(userId)))
                .andExpect(status().isCreated());
        return company;
    }

    Long company(Long userId, String body) throws Exception {
        return idOf(mockMvc.perform(post("/companies").with(asUser(userId)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn());
    }

    Long me(Long userId, Long companyId) throws Exception {
        String body = mockMvc.perform(get("/companies/{id}/partners", companyId).with(asUser(userId)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$[0].id", Long.class);
    }

    static String distribution(Long partnerId, String date, String gross, String irrf) {
        return """
                {"partnerId": %d, "kind": "DISTRIBUICAO_LUCROS", "paymentDate": "%s", "grossAmount": %s%s}
                """.formatted(partnerId, date, gross, irrf == null ? "" : ", \"irrfAmount\": " + irrf);
    }

    ResultActions distribute(Long userId, Long companyId, Long partnerId, String date, String gross, String irrf) throws Exception {
        return postWithdrawal(userId, companyId, distribution(partnerId, date, gross, irrf));
    }

    ResultActions postWithdrawal(Long userId, Long companyId, String body) throws Exception {
        return mockMvc.perform(post("/companies/{id}/withdrawals", companyId).with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
