package com.akiratochiro.life_and_money_api.company;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O faturamento esperado do mês (Fase 8), com vigência como o pró-labore planejado. */
class ExpectedRevenueIntegrationTest extends IntegrationTest {

    static final String COMPANY = """
            {"name": "Ana Design", "emoji": "🎨", "openingDate": "2026-01-01",
             "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "FATOR_R"}
            """;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-12T15:00:00Z"));
    }

    @Test
    void withoutAValueTheAnswerIsNoContent() throws Exception {
        Long ana = register("esperado.vazio@test.dev");
        Long company = company(ana);

        mockMvc.perform(get("/companies/{id}/expected-revenue", company).with(asUser(ana)))
                .andExpect(status().isNoContent());
    }

    /** Trocar no mesmo mês substitui; no mês seguinte, fecha novembro e abre dezembro. */
    @Test
    void changingInTheSameMonthReplacesAndInALaterMonthOpensANewPeriod() throws Exception {
        Long ana = register("esperado.vigencia@test.dev");
        Long company = company(ana);

        set(ana, company, "10000").andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(10000.00))
                .andExpect(jsonPath("$.validFrom").value("2026-11"));
        set(ana, company, "12000").andExpect(status().isOk());
        assertThat(periods(company)).hasSize(1);

        clock.setTo(Instant.parse("2026-12-03T15:00:00Z"));
        set(ana, company, "15000").andExpect(jsonPath("$.validFrom").value("2026-12"));

        assertThat(periods(company)).extracting(row -> row.get("amount").toString(), row -> String.valueOf(row.get("valid_to")))
                .containsExactly(org.assertj.core.groups.Tuple.tuple("12000.00", "2026-11-01"),
                        org.assertj.core.groups.Tuple.tuple("15000.00", "null"));
        mockMvc.perform(get("/companies/{id}/expected-revenue", company).with(asUser(ana)))
                .andExpect(jsonPath("$.amount").value(15000.00))
                .andExpect(jsonPath("$.validTo").doesNotExist());
    }

    /** Remover o que começou neste mês apaga; o que já tem passado é encerrado no mês anterior. */
    @Test
    void removingDeletesThisMonthsValueAndClosesAnOlderOne() throws Exception {
        Long ana = register("esperado.remover@test.dev");
        Long company = company(ana);

        set(ana, company, "10000");
        remove(ana, company).andExpect(status().isNoContent());
        assertThat(periods(company)).isEmpty();

        set(ana, company, "10000");
        clock.setTo(Instant.parse("2027-01-10T15:00:00Z"));
        remove(ana, company).andExpect(status().isNoContent());
        assertThat(periods(company)).singleElement()
                .satisfies(row -> assertThat(row.get("valid_to").toString()).isEqualTo("2026-12-01"));
        mockMvc.perform(get("/companies/{id}/expected-revenue", company).with(asUser(ana)))
                .andExpect(status().isNoContent());
    }

    @Test
    void theAmountMustBePositive() throws Exception {
        Long ana = register("esperado.validacao@test.dev");
        Long company = company(ana);

        set(ana, company, "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("O valor precisa ser maior que zero."));
    }

    @Test
    void anArchivedCompanyCannotChangeIt() throws Exception {
        Long ana = register("esperado.arquivada@test.dev");
        Long company = company(ana);
        mockMvc.perform(post("/companies/{id}/archive", company).with(asUser(ana))).andExpect(status().is2xxSuccessful());

        set(ana, company, "10000").andExpect(status().isConflict());
        remove(ana, company).andExpect(status().isConflict());
    }

    @Test
    void anotherUsersCompanyIsNotFound() throws Exception {
        Long ana = register("esperado.isolamento.ana@test.dev");
        Long bruno = register("esperado.isolamento.bruno@test.dev");
        Long anas = company(ana);

        mockMvc.perform(get("/companies/{id}/expected-revenue", anas).with(asUser(bruno))).andExpect(status().isNotFound());
        set(bruno, anas, "10000").andExpect(status().isNotFound());
        remove(bruno, anas).andExpect(status().isNotFound());
    }

    // Apoio

    Long company(Long userId) throws Exception {
        return idOf(mockMvc.perform(post("/companies").with(asUser(userId)).contentType(MediaType.APPLICATION_JSON).content(COMPANY))
                .andExpect(status().isCreated()).andReturn());
    }

    ResultActions set(Long userId, Long companyId, String amount) throws Exception {
        return mockMvc.perform(put("/companies/{id}/expected-revenue", companyId).with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": " + amount + "}"));
    }

    ResultActions remove(Long userId, Long companyId) throws Exception {
        return mockMvc.perform(delete("/companies/{id}/expected-revenue", companyId).with(asUser(userId)));
    }

    List<Map<String, Object>> periods(Long companyId) {
        return jdbc.queryForList("select amount, valid_to from company_expected_revenues where company_id = ? order by valid_from",
                companyId);
    }
}
