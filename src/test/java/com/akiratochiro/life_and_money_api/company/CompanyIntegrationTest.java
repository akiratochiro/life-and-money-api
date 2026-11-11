package com.akiratochiro.life_and_money_api.company;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyIntegrationTest extends IntegrationTest {

    static final String MEI = """
            {"name": "%s", "emoji": "💼", "openingDate": "2024-03-10",
             "legalNature": "MEI", "taxRegime": "SIMEI", "meiActivity": "SERVICOS"}
            """;

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    @Test
    void creatingACompanyStartsItsRegimeInTheOpeningMonthWithTheDefaults() throws Exception {
        Long ana = register("empresa.criar@test.dev");

        create(ana, """
                {"name": "  Ana Design  ", "emoji": "🎨", "cnpj": "12.abc.345/01de-35", "openingDate": "2024-03-10",
                 "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "FATOR_R"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ana Design"))
                .andExpect(jsonPath("$.emoji").value("🎨"))
                .andExpect(jsonPath("$.cnpj").value("12ABC34501DE35"))
                .andExpect(jsonPath("$.openingDate").value("2024-03-10"))
                .andExpect(jsonPath("$.openingDateEditable").value(true))
                .andExpect(jsonPath("$.revenueBasis").value("COMPETENCIA"))
                .andExpect(jsonPath("$.fatorRTarget").value(0.28))
                .andExpect(jsonPath("$.openingBalance").value(0))
                .andExpect(jsonPath("$.openingBalanceDate").value("2026-11-11"))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.currentRegime.legalNature").value("SLU"))
                .andExpect(jsonPath("$.currentRegime.taxRegime").value("SIMPLES_NACIONAL"))
                .andExpect(jsonPath("$.currentRegime.annexConfig").value("FATOR_R"))
                .andExpect(jsonPath("$.currentRegime.validFrom").value("2024-03"))
                .andExpect(jsonPath("$.currentRegime.validTo").value(nullValue()));
    }

    @Test
    void anInvalidRegimeCombinationMarksTheField() throws Exception {
        Long ana = register("empresa.regime@test.dev");

        create(ana, """
                {"name": "X", "emoji": "💼", "openingDate": "2024-03-10",
                 "legalNature": "MEI", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "FATOR_R", "meiActivity": "SERVICOS"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Confira os campos destacados e tente de novo."))
                .andExpect(jsonPath("$.errors.taxRegime").value("O MEI é sempre tributado pelo SIMEI."));
    }

    @Test
    void eachInvalidFieldGetsItsOwnMessage() throws Exception {
        Long ana = register("empresa.campos@test.dev");

        create(ana, """
                {"emoji": "💼", "openingDate": "2024-03-10", "legalNature": "MEI", "taxRegime": "SIMEI", "meiActivity": "SERVICOS"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Informe o nome da empresa."));
        create(ana, MEI.formatted("X").replace("💼", "Empresa"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.emoji").value("Use um único emoji."));
        create(ana, MEI.formatted("X").replace("\"emoji\"", "\"cnpj\": \"11.222.333/0001-82\", \"emoji\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cnpj").value("CNPJ inválido. Confira os números e as letras."));
        create(ana, MEI.formatted("X").replace("2024-03-10", "2026-11-12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.openingDate").value("A data de abertura não pode estar no futuro."));
        create(ana, MEI.formatted("X").replace("\"emoji\"", "\"openingBalanceDate\": \"2024-03-09\", \"emoji\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.openingBalanceDate").value(
                        "A data do saldo inicial (09/03/2024) não pode ser anterior à abertura da empresa (10/03/2024)."));
        create(ana, MEI.formatted("X").replace("\"emoji\"", "\"fatorRTarget\": 1.5, \"emoji\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fatorRTarget").value("A meta do Fator R não pode passar de 100%."));
    }

    @Test
    void theSameCnpjCannotBeRegisteredTwiceHoweverItIsWritten() throws Exception {
        Long ana = register("empresa.cnpj@test.dev");
        create(ana, MEI.formatted("Primeira").replace("\"emoji\"", "\"cnpj\": \"11.222.333/0001-81\", \"emoji\""))
                .andExpect(status().isCreated());

        create(ana, MEI.formatted("Segunda").replace("\"emoji\"", "\"cnpj\": \"11222333000181\", \"emoji\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.cnpj").value("Você já cadastrou uma empresa com este CNPJ."));

        // Outro usuário pode ter o mesmo CNPJ: cada conta é isolada (o sócio também usa o app).
        Long bruno = register("empresa.cnpj.bruno@test.dev");
        create(bruno, MEI.formatted("Do Bruno").replace("\"emoji\"", "\"cnpj\": \"11222333000181\", \"emoji\""))
                .andExpect(status().isCreated());
    }

    @Test
    void theListIsInPortugueseAlphabeticalOrderAndHidesArchivedCompanies() throws Exception {
        Long ana = register("empresa.lista@test.dev");
        create(ana, MEI.formatted("consultoria")).andExpect(status().isCreated());
        Long archived = idOf(create(ana, MEI.formatted("Antiga")).andReturn());
        create(ana, MEI.formatted("Ágil")).andExpect(status().isCreated());
        mockMvc.perform(post("/companies/{id}/archive", archived).with(asUser(ana))).andExpect(status().isOk());

        mockMvc.perform(get("/companies").with(asUser(ana)))
                .andExpect(jsonPath("$[*].name", contains("Ágil", "consultoria")));
        mockMvc.perform(get("/companies").param("includeArchived", "true").with(asUser(ana)))
                .andExpect(jsonPath("$[*].name", contains("Ágil", "Antiga", "consultoria")));
    }

    @Test
    void aPartialUpdateKeepsWhatWasNotSentAndAnEmptyCnpjRemovesIt() throws Exception {
        Long ana = register("empresa.editar@test.dev");
        Long company = idOf(create(ana, MEI.formatted("Antes").replace("\"emoji\"", "\"cnpj\": \"11222333000181\", \"emoji\""))
                .andReturn());

        update(ana, company, """
                {"name": "Depois", "fatorRTarget": 0.30, "openingBalance": -150.25, "openingBalanceDate": "2025-01-01"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Depois"))
                .andExpect(jsonPath("$.emoji").value("💼"))
                .andExpect(jsonPath("$.cnpj").value("11222333000181"))
                .andExpect(jsonPath("$.fatorRTarget").value(0.30))
                .andExpect(jsonPath("$.openingBalance").value(-150.25))
                .andExpect(jsonPath("$.openingBalanceDate").value("2025-01-01"));

        update(ana, company, """
                {"cnpj": ""}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cnpj").value(nullValue()))
                .andExpect(jsonPath("$.name").value("Depois"));
    }

    @Test
    void whileNothingDependsOnItTheOpeningDateMovesTheFirstRegimeAlong() throws Exception {
        Long ana = register("empresa.abertura@test.dev");
        Long company = idOf(create(ana, MEI.formatted("X").replace("\"emoji\"", "\"openingBalanceDate\": \"2024-06-01\", \"emoji\""))
                .andReturn());

        update(ana, company, """
                {"openingDate": "2023-08-20"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingDate").value("2023-08-20"))
                .andExpect(jsonPath("$.currentRegime.validFrom").value("2023-08"))
                .andExpect(jsonPath("$.openingDateEditable").value(true));

        // Se a abertura passar da data do saldo, o erro aparece no campo que o usuário mexeu.
        update(ana, company, """
                {"openingDate": "2024-07-01"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.openingDate").value(
                        "A data do saldo inicial (01/06/2024) não pode ser anterior à abertura da empresa (01/07/2024)."));
        update(ana, company, """
                {"openingDate": "2026-12-01"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.openingDate").value("A data de abertura não pode estar no futuro."));
    }

    @Test
    void anArchivedCompanyIsReadOnlyUntilUnarchived() throws Exception {
        Long ana = register("empresa.arquivar@test.dev");
        Long company = idOf(create(ana, MEI.formatted("Pausada")).andReturn());

        mockMvc.perform(post("/companies/{id}/archive", company).with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        // Arquivar de novo não é erro.
        mockMvc.perform(post("/companies/{id}/archive", company).with(asUser(ana)))
                .andExpect(status().isOk());

        update(ana, company, """
                {"name": "Nova"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta empresa está arquivada. Desarquive-a para fazer alterações."));
        mockMvc.perform(get("/companies/{id}", company).with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pausada"));

        mockMvc.perform(post("/companies/{id}/unarchive", company).with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
        update(ana, company, """
                {"name": "Nova"}
                """)
                .andExpect(status().isOk());
    }

    private ResultActions create(Long userId, String body) throws Exception {
        return mockMvc.perform(post("/companies").with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions update(Long userId, Long companyId, String body) throws Exception {
        return mockMvc.perform(patch("/companies/{id}", companyId).with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
