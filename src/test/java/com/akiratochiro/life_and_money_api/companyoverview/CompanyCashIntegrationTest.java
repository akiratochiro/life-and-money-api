package com.akiratochiro.life_and_money_api.companyoverview;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O caixa da empresa (decisão 29). A nota da fixture tem serviço de 10.000, líquido de 8.450 (ISS de
 * 200 retido e outras retenções), emissão em 10/11/2026 e competência em 31/10/2026.
 */
class CompanyCashIntegrationTest extends IntegrationTest {

    static final String KEY = "35503082212345678000195000000000004226111234567890";
    static final String OTHER_KEY = "35503082212345678000195000000000004326111234567891";

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    /**
     * Saldo inicial de 5.000 em 01/10, hoje 11/11:
     * <ul>
     *   <li>+ 8.450 da nota, pelo líquido;</li>
     *   <li>− 200 (Cadeira, 15/10) − 120 (Nuvem de outubro, paga em 05/10) − 401,50 (DAS de outubro, pago em
     *       10/11 e virado despesa de Impostos) = − 721,50;</li>
     *   <li>− 2.670 do pró-labore, pelo líquido (3.000 − 330 de INSS; o IRRF é zero);</li>
     * </ul>
     * saldo = 5.000 + 8.450 − 721,50 − 2.670 = 10.058,50. A provisão é o DARF de outubro (330, vence em
     * 19/11, ainda no prazo); a taxa sem valor fica fora e é contada. Disponível = 9.728,50.
     */
    @Test
    void theBalanceUsesNetAmountsAndTheProvisionHoldsWhatIsStillDue() throws Exception {
        Long ana = register("caixa.saldo@test.dev");
        Long company = exampleCompany(ana, "5000", "2026-10-01");

        cash(ana, company)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value("2026-11-11"))
                .andExpect(jsonPath("$.openingBalance").value(5000.00))
                .andExpect(jsonPath("$.balance.revenue").value(8450.00))
                .andExpect(jsonPath("$.balance.expenses").value(721.50))
                .andExpect(jsonPath("$.balance.withdrawals").value(2670.00))
                .andExpect(jsonPath("$.balance.total").value(10058.50))
                .andExpect(jsonPath("$.provision.currentMonth").value(0))
                .andExpect(jsonPath("$.provision.dueSoon").value(330.00))
                .andExpect(jsonPath("$.provision.overdue").value(0))
                .andExpect(jsonPath("$.provision.total").value(330.00))
                .andExpect(jsonPath("$.provision.withoutExpected").value(1))
                .andExpect(jsonPath("$.available").value(9728.50))
                .andExpect(jsonPath("$.governmentShare.month").value("2026-11"))
                .andExpect(jsonPath("$.governmentShare.revenue").value(0))
                .andExpect(jsonPath("$.governmentShare.amount").value(0))
                .andExpect(jsonPath("$.governmentShare.effectiveRate").value(nullValue()))
                .andExpect(jsonPath("$.governmentShare.kind").value("DAS"))
                .andExpect(jsonPath("$.notice").isNotEmpty());
    }

    /**
     * Mudando o saldo inicial para 1.000 em 15/10: a Cadeira (15/10) entra, porque o saldo vale do começo
     * do dia; a Nuvem (05/10) sai. 1.000 + 8.450 − (200 + 401,50) − 2.670 = 6.178,50.
     */
    @Test
    void theOpeningBalanceCountsFromTheStartOfItsDay() throws Exception {
        Long ana = register("caixa.dia@test.dev");
        Long company = exampleCompany(ana, "5000", "2026-10-01");

        mockMvc.perform(patch("/companies/{id}", company).with(asUser(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingBalance\": 1000, \"openingBalanceDate\": \"2026-10-15\"}"))
                .andExpect(status().isOk());

        cash(ana, company)
                .andExpect(jsonPath("$.balance.expenses").value(601.50))
                .andExpect(jsonPath("$.balance.total").value(6178.50));
    }

    /** Em 09/11, a nota (emitida em 10/11) e o DAS pago em 10/11 ainda não aconteceram. */
    @Test
    void nothingAfterTodayEntersTheBalance() throws Exception {
        Long ana = register("caixa.futuro@test.dev");
        Long company = exampleCompany(ana, "5000", "2026-10-01");
        clock.setTo(Instant.parse("2026-11-09T15:00:00Z"));

        cash(ana, company)
                .andExpect(jsonPath("$.balance.revenue").value(0))
                .andExpect(jsonPath("$.balance.expenses").value(320.00))
                .andExpect(jsonPath("$.balance.total").value(2010.00));
    }

    /**
     * Em 25/11, sem nada pago: o DAS de outubro (venceu em 23/11, porque 20/11 é feriado) está atrasado, e o de novembro, sobre a
     * segunda nota (competência 05/11), é do mês corrente. A cancelada não entra em nada. O saldo inicial
     * de −20.000 deixa o disponível negativo.
     */
    @Test
    void overdueAndCurrentTaxesAreProvisionedAndAvailableMayBeNegative() throws Exception {
        clock.setTo(Instant.parse("2026-11-25T15:00:00Z"));
        Long ana = register("caixa.provisao@test.dev");
        Long company = company(ana, """
                {"name": "Ana Design", "emoji": "🎨", "openingDate": "2026-10-01", "openingBalance": -20000,
                 "openingBalanceDate": "2026-10-01", "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL",
                 "annexConfig": "ANEXO_III_FIXO"}
                """);
        saveXml(ana, company, xml());
        saveXml(ana, company, secondXml().replace("<dCompet>2026-10-31</dCompet>", "<dCompet>2026-11-05</dCompet>"));
        Long cancelled = saveXml(ana, company, thirdXml().replace("<dCompet>2026-10-31</dCompet>", "<dCompet>2026-11-06</dCompet>"));
        mockMvc.perform(post("/companies/{id}/invoices/{iid}/cancel", company, cancelled).with(asUser(ana)))
                .andExpect(status().isOk());

        BigDecimal october = dasExpected(ana, company, "2026-11");
        BigDecimal november = dasExpected(ana, company, "2026-12");
        BigDecimal balance = new BigDecimal("-20000").add(new BigDecimal("16900"));

        cash(ana, company)
                .andExpect(jsonPath("$.balance.revenue").value(16900.00))
                .andExpect(jsonPath("$.balance.total").value(balance.doubleValue()))
                .andExpect(jsonPath("$.provision.overdue").value(october.doubleValue()))
                .andExpect(jsonPath("$.provision.currentMonth").value(november.doubleValue()))
                .andExpect(jsonPath("$.provision.dueSoon").value(0))
                .andExpect(jsonPath("$.provision.total").value(october.add(november).doubleValue()))
                .andExpect(jsonPath("$.available").value(balance.subtract(october).subtract(november).doubleValue()))
                .andExpect(jsonPath("$.governmentShare.revenue").value(10000.00))
                .andExpect(jsonPath("$.governmentShare.amount").value(november.doubleValue()))
                .andExpect(jsonPath("$.governmentShare.effectiveRate")
                        .value(november.divide(new BigDecimal("10000"), 4, RoundingMode.HALF_UP).doubleValue()));
    }

    /** Pagar o DAS atrasado tira ele da provisão e põe a despesa no saldo: o imposto conta uma vez só. */
    @Test
    void aPaidTaxLeavesTheProvisionAndEntersTheBalanceOnce() throws Exception {
        clock.setTo(Instant.parse("2026-11-25T15:00:00Z"));
        Long ana = register("caixa.pago@test.dev");
        Long company = company(ana, """
                {"name": "Ana Design", "emoji": "🎨", "openingDate": "2026-10-01", "openingBalanceDate": "2026-10-01",
                 "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "ANEXO_III_FIXO"}
                """);
        saveXml(ana, company, xml());
        BigDecimal october = dasExpected(ana, company, "2026-11");
        String before = cash(ana, company).andReturn().getResponse().getContentAsString();
        BigDecimal availableBefore = new BigDecimal(JsonPath.parse(before).read("$.available").toString());

        Long das = obligation(ana, company, "DAS");
        mockMvc.perform(post("/companies/{id}/taxes/{oid}/occurrences/{c}/payment", company, das, "2026-10").with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": %s, \"date\": \"2026-11-25\"}".formatted(october.toPlainString())))
                .andExpect(status().isCreated());

        cash(ana, company)
                .andExpect(jsonPath("$.balance.expenses").value(october.doubleValue()))
                .andExpect(jsonPath("$.provision.overdue").value(0))
                .andExpect(jsonPath("$.available").value(availableBefore.doubleValue()));
    }

    @Test
    void theMeiShareIsTheFixedDasWithoutRate() throws Exception {
        Long ana = register("caixa.mei@test.dev");
        Long company = company(ana, """
                {"name": "Ana MEI", "emoji": "🎨", "openingDate": "2026-10-01", "openingBalanceDate": "2026-10-01",
                 "legalNature": "MEI", "taxRegime": "SIMEI", "meiActivity": "SERVICOS"}
                """);

        cash(ana, company)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.governmentShare.kind").value("DAS_MEI"))
                .andExpect(jsonPath("$.governmentShare.amount").isNumber())
                .andExpect(jsonPath("$.governmentShare.effectiveRate").value(nullValue()));
    }

    @Test
    void lucroPresumidoHasNoGovernmentShare() throws Exception {
        Long ana = register("caixa.lp@test.dev");
        Long company = company(ana, """
                {"name": "Ana LP", "emoji": "🎨", "openingDate": "2026-10-01", "openingBalanceDate": "2026-10-01",
                 "legalNature": "LTDA", "taxRegime": "LUCRO_PRESUMIDO"}
                """);

        cash(ana, company)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.governmentShare").doesNotExist())
                .andExpect(jsonPath("$.provision.total").value(0));
    }

    @Test
    void anotherUsersCashIsNotFound() throws Exception {
        Long ana = register("caixa.isolamento.ana@test.dev");
        Long bruno = register("caixa.isolamento.bruno@test.dev");
        Long anas = exampleCompany(ana, "0", "2026-10-01");

        cash(bruno, anas).andExpect(status().isNotFound());
    }

    // Apoio

    /** A empresa da {@link CompanyOverviewIntegrationTest}, com o saldo inicial informado. */
    Long exampleCompany(Long userId, String openingBalance, String openingBalanceDate) throws Exception {
        Long company = company(userId, """
                {"name": "Ana Design", "emoji": "🎨", "openingDate": "2026-10-01", "openingBalance": %s,
                 "openingBalanceDate": "%s", "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL",
                 "annexConfig": "ANEXO_III_FIXO"}
                """.formatted(openingBalance, openingBalanceDate));
        saveXml(userId, company, xml());

        Long software = categoryId(userId, company, "Software");
        postJson(userId, "/companies/{id}/expenses", company, """
                {"name": "Cadeira", "amount": 200, "categoryId": %d, "date": "2026-10-15"}
                """.formatted(software)).andExpect(status().isCreated());
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
        postJson(userId, "/companies/{id}/obligations", company, """
                {"name": "Taxa de licença", "recurrence": "MONTHLY", "dueDay": 10, "dueRule": "NONE", "startMonth": "2026-11"}
                """).andExpect(status().isCreated());
        return company;
    }

    Long company(Long userId, String body) throws Exception {
        return idOf(postJson(userId, "/companies", null, body).andExpect(status().isCreated()).andReturn());
    }

    String xml() throws Exception {
        try (var in = getClass().getResourceAsStream("/nfse/nfse-v101.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    String secondXml() throws Exception {
        return xml().replace("NFS" + KEY, "NFS" + OTHER_KEY).replace("<nNFSe>42</nNFSe>", "<nNFSe>43</nNFSe>");
    }

    String thirdXml() throws Exception {
        String third = KEY.substring(0, KEY.length() - 1) + "2";
        return xml().replace("NFS" + KEY, "NFS" + third).replace("<nNFSe>42</nNFSe>", "<nNFSe>44</nNFSe>");
    }

    Long saveXml(Long userId, Long companyId, String xml) throws Exception {
        return idOf(mockMvc.perform(multipart("/companies/{id}/invoices", companyId)
                        .file(new MockMultipartFile("file", "nota.xml", "application/xml", xml.getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(userId)))
                .andExpect(status().isCreated()).andReturn());
    }

    /** O previsto do DAS que vence no mês, lido da aba Impostos: o caixa não refaz a conta do motor. */
    BigDecimal dasExpected(Long userId, Long companyId, String dueMonth) throws Exception {
        String body = mockMvc.perform(get("/companies/{id}/taxes", companyId).param("month", dueMonth).with(asUser(userId)))
                .andReturn().getResponse().getContentAsString();
        List<Object> values = JsonPath.parse(body).read("$.items[?(@.kind == 'DAS')].expectedAmount");
        return new BigDecimal(values.getFirst().toString());
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

    ResultActions cash(Long userId, Long companyId) throws Exception {
        return mockMvc.perform(get("/companies/{id}/cash", companyId).with(asUser(userId)));
    }
}
