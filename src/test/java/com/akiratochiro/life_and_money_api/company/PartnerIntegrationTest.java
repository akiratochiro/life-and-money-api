package com.akiratochiro.life_and_money_api.company;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PartnerIntegrationTest extends IntegrationTest {

    private static final String LTDA = """
            {"name": "Sociedade", "emoji": "🤝", "openingDate": "2024-03-10", "partnerName": "Ana Souza",
             "legalNature": "LTDA", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "FATOR_R"}
            """;

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    @Test
    void theCompanyStartsWithYouAsAPartnerUnderTheNameYouChose() throws Exception {
        Long ana = register("socio.inicio@test.dev");
        Long company = createCompany(ana, LTDA);

        mockMvc.perform(get("/companies/{id}/partners", company).with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Ana Souza"))
                .andExpect(jsonPath("$[0].me").value(true));
    }

    @Test
    void withoutAPartnerNameTheAccountNameIsUsed() throws Exception {
        Long ana = register("socio.padrao@test.dev");
        Long company = createCompany(ana, CompanyIntegrationTest.MEI.formatted("Solo"));

        mockMvc.perform(get("/companies/{id}/partners", company).with(asUser(ana)))
                .andExpect(jsonPath("$[0].name").value("Teste"));
    }

    @Test
    void otherPartnersCanBeAddedRenamedAndRemovedButNotYou() throws Exception {
        Long ana = register("socio.ciclo@test.dev");
        Long company = createCompany(ana, LTDA);
        Long me = JsonPath.parse(mockMvc.perform(get("/companies/{id}/partners", company).with(asUser(ana)))
                .andReturn().getResponse().getContentAsString()).read("$[0].id", Long.class);

        Long bruno = idOf(addPartner(ana, company, "Bruno").andExpect(status().isCreated())
                .andExpect(jsonPath("$.me").value(false))
                .andReturn());
        mockMvc.perform(patch("/companies/{id}/partners/{pid}", company, bruno).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Bruno Lima\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bruno Lima"));
        mockMvc.perform(get("/companies/{id}/partners", company).with(asUser(ana)))
                .andExpect(jsonPath("$[*].name", contains("Ana Souza", "Bruno Lima")));

        mockMvc.perform(delete("/companies/{id}/partners/{pid}", company, me).with(asUser(ana)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Você não pode se remover da sua própria empresa."));
        mockMvc.perform(delete("/companies/{id}/partners/{pid}", company, bruno).with(asUser(ana)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/companies/{id}/partners", company).with(asUser(ana)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aMeiHasNoPartnersBesidesItsOwner() throws Exception {
        Long ana = register("socio.mei@test.dev");
        Long company = createCompany(ana, CompanyIntegrationTest.MEI.formatted("Solo"));

        addPartner(ana, company, "Bruno")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("O MEI não tem sócios além do titular."));
    }

    @Test
    void aCompanyWithPartnersCannotBecomeAMei() throws Exception {
        Long ana = register("socio.virar.mei@test.dev");
        Long company = createCompany(ana, LTDA);
        addPartner(ana, company, "Bruno").andExpect(status().isCreated());

        mockMvc.perform(post("/companies/{id}/regimes", company).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validFrom": "2025-01", "legalNature": "MEI", "taxRegime": "SIMEI", "meiActivity": "AMBOS"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("O MEI não tem sócios além do titular. Remova os outros sócios antes de mudar para MEI."));
    }

    @Test
    void theNameIsRequired() throws Exception {
        Long ana = register("socio.nome@test.dev");
        Long company = createCompany(ana, LTDA);

        addPartner(ana, company, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Informe o nome do sócio."));
    }

    @Test
    void partnersOfAnotherUsersCompanyOrOfAnotherCompanyAreNotFound() throws Exception {
        Long ana = register("socio.isolamento.ana@test.dev");
        Long bruno = register("socio.isolamento.bruno@test.dev");
        Long anas = createCompany(ana, LTDA);
        Long brunos = createCompany(bruno, LTDA);
        Long anasPartner = idOf(addPartner(ana, anas, "Carla").andReturn());

        mockMvc.perform(get("/companies/{id}/partners", anas).with(asUser(bruno)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Empresa não encontrada."));
        addPartner(bruno, anas, "Intruso").andExpect(status().isNotFound());
        // O sócio da Ana pelo caminho da empresa do Bruno: a empresa é dele, o sócio não é dela.
        mockMvc.perform(delete("/companies/{id}/partners/{pid}", brunos, anasPartner).with(asUser(bruno)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Sócio não encontrado."));
    }

    @Test
    void anArchivedCompanyKeepsItsPartnersReadOnly() throws Exception {
        Long ana = register("socio.arquivada@test.dev");
        Long company = createCompany(ana, LTDA);
        mockMvc.perform(post("/companies/{id}/archive", company).with(asUser(ana))).andExpect(status().isOk());

        mockMvc.perform(get("/companies/{id}/partners", company).with(asUser(ana))).andExpect(status().isOk());
        addPartner(ana, company, "Bruno").andExpect(status().isConflict());
    }

    private Long createCompany(Long userId, String body) throws Exception {
        return idOf(mockMvc.perform(post("/companies").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private ResultActions addPartner(Long userId, Long companyId, String name) throws Exception {
        return mockMvc.perform(post("/companies/{id}/partners", companyId).with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\"}".formatted(name)));
    }
}
