package com.akiratochiro.life_and_money_api.companyoverview;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Os limites de faturamento (decisão 32), hoje 12/11/2026. */
class RevenueLimitsIntegrationTest extends IntegrationTest {

    static final String KEY = "35503082212345678000195000000000004226111234567890";
    int notes;

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-12T15:00:00Z"));
    }

    /**
     * MEI aberto em 20/09/2026: 4 meses × R$ 6.750 = R$ 27.000. Notas de 5.000, 8.000 e 8.450 = 21.450 (79%).
     * Pela média de setembro e outubro (6.500), dezembro leva o ano a 27.950 e passa do limite.
     */
    @Test
    void aMeiInItsOpeningYearWarnsAndProjectsByTheAverage() throws Exception {
        Long ana = register("limites.mei@test.dev");
        Long company = mei(ana);

        limits(ana, company, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applies").value(true))
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.years", contains(2026)))
                .andExpect(jsonPath("$.regime").value("SIMEI"))
                .andExpect(jsonPath("$.openingYear").value(true))
                .andExpect(jsonPath("$.activityMonths").value(4))
                .andExpect(jsonPath("$.revenue").value(21450.00))
                .andExpect(jsonPath("$.projection.total").value(27950.00))
                .andExpect(jsonPath("$.projection.basis").value("AVERAGE"))
                .andExpect(jsonPath("$.alert").value("MEI_ANNUAL"))
                .andExpect(jsonPath("$.conditional").doesNotExist())
                .andExpect(jsonPath("$.limits[0].label").value("Limite do MEI"))
                .andExpect(jsonPath("$.limits[0].amount").value(27000.00))
                .andExpect(jsonPath("$.limits[0].toleranceAmount").value(32400.00))
                .andExpect(jsonPath("$.limits[0].ratio").value(0.7944))
                .andExpect(jsonPath("$.limits[0].level").value("WARNING"))
                .andExpect(jsonPath("$.limits[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.limits[0].projectedIn").value("2026-12"))
                .andExpect(jsonPath("$.limits[0].message").value(
                        "Você já faturou 79% do limite do MEI em 2026 (R$ 21.450,00 de R$ 27.000,00). Acima dele, a empresa deixa de ser MEI."))
                .andExpect(jsonPath("$.limits[0].projectionMessage").value(
                        "No ritmo atual, a receita passa do limite do MEI em dezembro de 2026."))
                .andExpect(jsonPath("$.notice").isNotEmpty());
    }

    /** Com faturamento esperado de 9.000: novembro vale 9.000 (mais que as notas) e dezembro também: 31.000. */
    @Test
    void theExpectedRevenueDrivesTheProjection() throws Exception {
        Long ana = register("limites.esperado@test.dev");
        Long company = mei(ana);
        mockMvc.perform(put("/companies/{id}/expected-revenue", company).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 9000}"))
                .andExpect(status().isOk());

        limits(ana, company, null)
                .andExpect(jsonPath("$.projection.total").value(31000.00))
                .andExpect(jsonPath("$.projection.basis").value("EXPECTED_REVENUE"));
    }

    /** Simples com 200.000 em março e em abril: passa de 360.000 em abril e vira EPP em janeiro, um aviso só. */
    @Test
    void theSimplesBecomesEppAsInformation() throws Exception {
        Long ana = register("limites.epp@test.dev");
        Long company = company(ana, "2026-01-01", "\"legalNature\": \"SLU\", \"taxRegime\": \"SIMPLES_NACIONAL\", \"annexConfig\": \"ANEXO_III_FIXO\"");
        note(ana, company, "2026-03-10", "200000.00");
        note(ana, company, "2026-04-10", "200000.00");

        limits(ana, company, null)
                .andExpect(jsonPath("$.limits[*].kind", contains("SIMPLES_EPP", "SIMPLES_SUBLIMIT", "SIMPLES_CEILING")))
                .andExpect(jsonPath("$.limits[0].level").value("EXCEEDED"))
                .andExpect(jsonPath("$.limits[0].severity").value("INFO"))
                .andExpect(jsonPath("$.limits[0].exceededIn").value("2026-04"))
                .andExpect(jsonPath("$.limits[0].effectiveFrom").value("2027-01"))
                .andExpect(jsonPath("$.limits[0].message").value("A receita de 2026 passou de R$ 360.000,00 em abril de 2026: a partir "
                        + "de janeiro de 2027, a empresa é EPP. No Simples, os impostos não mudam por isso; muda o porte no cadastro."))
                .andExpect(jsonPath("$.limits[1].level").value("NONE"))
                .andExpect(jsonPath("$.limits[1].message").doesNotExist())
                .andExpect(jsonPath("$.alert").value("SIMPLES_EPP"));
    }

    /** O MEI que virou Simples em julho vê os limites do Simples, sobre a receita do ano todo. */
    @Test
    void theRegimeInForceTodayCountsForTheWholeYear() throws Exception {
        Long ana = register("limites.mudanca@test.dev");
        Long company = company(ana, "2026-01-01", "\"legalNature\": \"MEI\", \"taxRegime\": \"SIMEI\", \"meiActivity\": \"SERVICOS\"");
        note(ana, company, "2026-03-10", "50000.00");
        mockMvc.perform(post("/companies/{id}/regimes", company).with(asUser(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validFrom\": \"2026-07\", \"legalNature\": \"SLU\", \"taxRegime\": \"SIMPLES_NACIONAL\", \"annexConfig\": \"ANEXO_III_FIXO\"}"))
                .andExpect(status().is2xxSuccessful());

        limits(ana, company, null)
                .andExpect(jsonPath("$.regime").value("SIMPLES_NACIONAL"))
                .andExpect(jsonPath("$.revenue").value(50000.00))
                .andExpect(jsonPath("$.limits[0].kind").value("SIMPLES_EPP"));
    }

    /**
     * Em fevereiro de 2027: 2027 usa o limite cheio de R$ 110.000 com a ressalva da LC 238; 2026, já fechado,
     * mostra como terminou, sem projeção.
     */
    @Test
    void aPastYearHasNoProjectionAndTheNextMeiLimitIsConditional() throws Exception {
        Long ana = register("limites.anos@test.dev");
        Long company = mei(ana);
        clock.setTo(Instant.parse("2027-02-10T15:00:00Z"));

        limits(ana, company, null)
                .andExpect(jsonPath("$.year").value(2027))
                .andExpect(jsonPath("$.years", contains(2027, 2026)))
                .andExpect(jsonPath("$.openingYear").value(false))
                .andExpect(jsonPath("$.limits[0].amount").value(110000.00))
                .andExpect(jsonPath("$.conditional").isNotEmpty());
        limits(ana, company, 2026)
                .andExpect(jsonPath("$.limits[0].amount").value(27000.00))
                .andExpect(jsonPath("$.revenue").value(21450.00))
                .andExpect(jsonPath("$.projection").doesNotExist())
                .andExpect(jsonPath("$.limits[0].projectedIn").doesNotExist());
    }

    @Test
    void lucroPresumidoHasNoLimits() throws Exception {
        Long ana = register("limites.lp@test.dev");
        Long company = company(ana, "2026-01-01", "\"legalNature\": \"LTDA\", \"taxRegime\": \"LUCRO_PRESUMIDO\"");

        limits(ana, company, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applies").value(false))
                .andExpect(jsonPath("$.limits").isEmpty());
    }

    @Test
    void theYearMustBeAvailable() throws Exception {
        Long ana = register("limites.ano@test.dev");
        Long company = mei(ana);

        limits(ana, company, 2025)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Os limites de faturamento estão disponíveis só para 2026."));
    }

    @Test
    void anotherUsersLimitsAreNotFound() throws Exception {
        Long ana = register("limites.isolamento.ana@test.dev");
        Long bruno = register("limites.isolamento.bruno@test.dev");
        Long anas = mei(ana);

        limits(bruno, anas, null).andExpect(status().isNotFound());
    }

    // Apoio

    Long mei(Long userId) throws Exception {
        Long company = company(userId, "2026-09-20", "\"legalNature\": \"MEI\", \"taxRegime\": \"SIMEI\", \"meiActivity\": \"SERVICOS\"");
        note(userId, company, "2026-09-25", "5000.00");
        note(userId, company, "2026-10-15", "8000.00");
        note(userId, company, "2026-11-05", "8450.00");
        return company;
    }

    Long company(Long userId, String openingDate, String regime) throws Exception {
        return idOf(mockMvc.perform(post("/companies").with(asUser(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ana\", \"emoji\": \"🎨\", \"openingDate\": \"" + openingDate + "\", " + regime + "}"))
                .andExpect(status().isCreated()).andReturn());
    }

    /** Uma nota da fixture com outra chave, outra competência e outro valor do serviço. */
    void note(Long userId, Long companyId, String competence, String amount) throws Exception {
        notes++;
        String xml;
        try (var in = getClass().getResourceAsStream("/nfse/nfse-v101.xml")) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String key = KEY.substring(0, KEY.length() - 2) + String.format("%02d", 10 + notes);
        xml = xml.replace("NFS" + KEY, "NFS" + key).replace("<nNFSe>42</nNFSe>", "<nNFSe>" + (100 + notes) + "</nNFSe>")
                .replace("<dCompet>2026-10-31</dCompet>", "<dCompet>" + competence + "</dCompet>")
                .replace("<vServPrest><vServ>10000.00</vServ></vServPrest>", "<vServPrest><vServ>" + amount + "</vServ></vServPrest>");
        mockMvc.perform(multipart("/companies/{id}/invoices", companyId)
                        .file(new MockMultipartFile("file", "nota.xml", "application/xml", xml.getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(userId)))
                .andExpect(status().isCreated());
    }

    ResultActions limits(Long userId, Long companyId, Integer year) throws Exception {
        var request = get("/companies/{id}/revenue-limits", companyId).with(asUser(userId));
        if (year != null) {
            request = request.param("year", year.toString());
        }
        return mockMvc.perform(request);
    }
}
