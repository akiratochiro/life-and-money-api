package com.akiratochiro.life_and_money_api.company;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyRegimeIntegrationTest extends IntegrationTest {

    private static final String TO_SIMPLES = """
            {"validFrom": "%s", "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "FATOR_R"}
            """;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    @Test
    void aChangeInThePastClosesTheCurrentRegimeAndKeepsTheOldMonthsAsTheyWere() throws Exception {
        Long ana = register("regime.passado@test.dev");
        Long company = createMei(ana);

        change(ana, company, TO_SIMPLES.formatted("2025-03"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[*].taxRegime", contains("SIMEI", "SIMPLES_NACIONAL")))
                .andExpect(jsonPath("$[0].validFrom").value("2024-03"))
                .andExpect(jsonPath("$[0].validTo").value("2025-02"))
                .andExpect(jsonPath("$[1].validFrom").value("2025-03"))
                .andExpect(jsonPath("$[1].validTo").value(nullValue()));

        mockMvc.perform(get("/companies/{id}", company).with(asUser(ana)))
                .andExpect(jsonPath("$.currentRegime.taxRegime").value("SIMPLES_NACIONAL"))
                // Depois de uma mudança, a primeira vigência deixa de começar só pela abertura.
                .andExpect(jsonPath("$.openingDateEditable").value(false));
    }

    @Test
    void aChangeInTheFutureIsScheduledAndTheCurrentRegimeStaysUntilThen() throws Exception {
        Long ana = register("regime.futuro@test.dev");
        Long company = createMei(ana);

        change(ana, company, TO_SIMPLES.formatted("2027-01")).andExpect(status().isCreated());

        mockMvc.perform(get("/companies/{id}", company).with(asUser(ana)))
                .andExpect(jsonPath("$.currentRegime.taxRegime").value("SIMEI"))
                .andExpect(jsonPath("$.currentRegime.validTo").value("2026-12"));
    }

    @Test
    void aChangeCannotRewriteThePast() throws Exception {
        Long ana = register("regime.reescrever@test.dev");
        Long company = createMei(ana);
        change(ana, company, TO_SIMPLES.formatted("2025-03")).andExpect(status().isCreated());

        change(ana, company, """
                {"validFrom": "2025-03", "legalNature": "LTDA", "taxRegime": "LUCRO_PRESUMIDO"}
                """)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("A mudança precisa começar depois de 03/2025, quando começou "
                        + "o enquadramento atual. Para corrigir o atual, desfaça a última mudança."));
        change(ana, company, """
                {"validFrom": "2024-06", "legalNature": "LTDA", "taxRegime": "LUCRO_PRESUMIDO"}
                """)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void aChangeThatChangesNothingIsRejected() throws Exception {
        Long ana = register("regime.igual@test.dev");
        Long company = createMei(ana);

        change(ana, company, """
                {"validFrom": "2025-01", "legalNature": "MEI", "taxRegime": "SIMEI", "meiActivity": "SERVICOS"}
                """)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("O enquadramento informado é igual ao atual."));
    }

    @Test
    void theCombinationRulesApplyToEveryChange() throws Exception {
        Long ana = register("regime.regras@test.dev");
        Long company = createMei(ana);

        change(ana, company, """
                {"validFrom": "2025-01", "legalNature": "LTDA", "taxRegime": "SIMPLES_NACIONAL"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.annexConfig")
                        .value("Escolha como o Anexo é definido: Anexo III fixo ou pelo Fator R."));
        change(ana, company, """
                {"legalNature": "LTDA", "taxRegime": "LUCRO_REAL"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.validFrom").value("Informe a partir de que mês o novo enquadramento vale."));
    }

    @Test
    void undoingReopensThePreviousRegime() throws Exception {
        Long ana = register("regime.desfazer@test.dev");
        Long company = createMei(ana);
        change(ana, company, TO_SIMPLES.formatted("2025-03")).andExpect(status().isCreated());

        mockMvc.perform(delete("/companies/{id}/regimes/latest", company).with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].taxRegime").value("SIMEI"))
                .andExpect(jsonPath("$[0].validTo").value(nullValue()));

        // Com o histórico de volta a uma vigência só, a data de abertura volta a ser editável.
        mockMvc.perform(get("/companies/{id}", company).with(asUser(ana)))
                .andExpect(jsonPath("$.openingDateEditable").value(true));
    }

    @Test
    void theRegimeSinceTheOpeningCannotBeUndone() throws Exception {
        Long ana = register("regime.unico@test.dev");
        Long company = createMei(ana);

        mockMvc.perform(delete("/companies/{id}/regimes/latest", company).with(asUser(ana)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Não há mudança de enquadramento para desfazer: este é o enquadramento desde a abertura."));
    }

    @Test
    void afterARegimeChangeTheOpeningDateIsLocked() throws Exception {
        Long ana = register("regime.trava@test.dev");
        Long company = createMei(ana);
        change(ana, company, TO_SIMPLES.formatted("2025-03")).andExpect(status().isCreated());

        mockMvc.perform(patch("/companies/{id}", company).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"openingDate": "2023-01-15"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.openingDate").value("A data de abertura não pode mais ser alterada: "
                        + "a empresa já tem mudança de enquadramento ou notas fiscais."));

        // Mandar a mesma data de antes não é uma mudança, e o resto da edição passa.
        mockMvc.perform(patch("/companies/{id}", company).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"openingDate": "2024-03-10", "name": "Renomeada"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void anArchivedCompanyCannotChangeItsRegimeAndAnotherUserGets404() throws Exception {
        Long ana = register("regime.arquivada@test.dev");
        Long bruno = register("regime.arquivada.bruno@test.dev");
        Long company = createMei(ana);

        change(bruno, company, TO_SIMPLES.formatted("2025-03")).andExpect(status().isNotFound());
        mockMvc.perform(get("/companies/{id}/regimes", company).with(asUser(bruno))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/companies/{id}/regimes/latest", company).with(asUser(bruno)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/companies/{id}/archive", company).with(asUser(ana))).andExpect(status().isOk());
        change(ana, company, TO_SIMPLES.formatted("2025-03")).andExpect(status().isConflict());
    }

    /** O banco repete as regras: mesmo sem passar pelo service, MEI fora do SIMEI não entra. */
    @Test
    void theDatabaseRejectsAnInvalidCombinationOnItsOwn() throws Exception {
        Long ana = register("regime.banco@test.dev");
        Long company = createMei(ana);

        assertThatThrownBy(() -> jdbc.update("""
                insert into company_regimes (user_id, company_id, legal_nature, tax_regime, mei_activity, valid_from, valid_to)
                values (?, ?, 'MEI', 'LUCRO_REAL', 'SERVICOS', date '2020-01-01', date '2020-01-01')
                """, ana, company))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_company_regimes_mei_simei");
    }

    private Long createMei(Long userId) throws Exception {
        return idOf(mockMvc.perform(post("/companies").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CompanyIntegrationTest.MEI.formatted("Empresa")))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private ResultActions change(Long userId, Long companyId, String body) throws Exception {
        return mockMvc.perform(post("/companies/{id}/regimes", companyId).with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
