package com.akiratochiro.life_and_money_api.companyexpense;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExpenseCategoryIntegrationTest extends IntegrationTest {

    static final String COMPANY = """
            {"name": "Ana Design", "emoji": "🎨", "openingDate": "2024-03-10",
             "legalNature": "SLU", "taxRegime": "SIMPLES_NACIONAL", "annexConfig": "FATOR_R"}
            """;

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    @Test
    void aNewCompanyComesWithTheDefaultCategoriesInPortugueseOrder() throws Exception {
        Long ana = register("categoria.pj.padrao@test.dev");
        Long company = createCompany(ana);

        list(ana, company)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains(
                        "Aluguel", "Contador", "Equipamentos", "Impostos", "Outros", "Salários e encargos", "Software")))
                .andExpect(jsonPath("$[?(@.name == 'Impostos')].system").value(true))
                .andExpect(jsonPath("$[?(@.name == 'Salários e encargos')].countsAsPayroll").value(true))
                .andExpect(jsonPath("$[?(@.name == 'Aluguel')].icon").value("HOME"));
    }

    @Test
    void categoriesCanBeCreatedEditedAndArchived() throws Exception {
        Long ana = register("categoria.pj.ciclo@test.dev");
        Long company = createCompany(ana);

        Long marketing = idOf(mockMvc.perform(post("/companies/{id}/expense-categories", company).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \" Marketing \", \"color\": \"PINK\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Marketing"))
                .andExpect(jsonPath("$.icon").value("OTHER"))
                .andExpect(jsonPath("$.countsAsPayroll").value(false))
                .andReturn());

        mockMvc.perform(patch("/companies/{id}/expense-categories/{cid}", company, marketing).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Anúncios\", \"countsAsPayroll\": true}"))
                .andExpect(jsonPath("$.name").value("Anúncios"))
                .andExpect(jsonPath("$.color").value("PINK"))
                .andExpect(jsonPath("$.countsAsPayroll").value(true));

        mockMvc.perform(post("/companies/{id}/expense-categories/{cid}/archive", company, marketing).with(asUser(ana)))
                .andExpect(jsonPath("$.archived").value(true));
        list(ana, company).andExpect(jsonPath("$[*].name", not(hasItem("Anúncios"))));
    }

    @Test
    void aRepeatedNameIsRefused() throws Exception {
        Long ana = register("categoria.pj.repetida@test.dev");
        Long company = createCompany(ana);

        mockMvc.perform(post("/companies/{id}/expense-categories", company).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"aluguel\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe uma categoria ativa com esse nome nesta empresa."));
    }

    @Test
    void theTaxesCategoryBelongsToTheSystem() throws Exception {
        Long ana = register("categoria.pj.sistema@test.dev");
        Long company = createCompany(ana);
        Long taxes = idByName(ana, company, "Impostos");
        String message = "A categoria Impostos é do sistema: ela recebe os impostos pagos e não pode ser escolhida, "
                + "alterada nem arquivada.";

        mockMvc.perform(patch("/companies/{id}/expense-categories/{cid}", company, taxes).with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Tributos\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(message));
        mockMvc.perform(post("/companies/{id}/expense-categories/{cid}/archive", company, taxes).with(asUser(ana)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void anotherUsersCategoriesAreNotFound() throws Exception {
        Long ana = register("categoria.pj.isolamento.ana@test.dev");
        Long bruno = register("categoria.pj.isolamento.bruno@test.dev");
        Long anas = createCompany(ana);
        Long brunos = createCompany(bruno);
        Long anasRent = idByName(ana, anas, "Aluguel");

        list(bruno, anas).andExpect(status().isNotFound());
        mockMvc.perform(post("/companies/{id}/expense-categories/{cid}/archive", brunos, anasRent).with(asUser(bruno)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Categoria não encontrada."));
    }

    Long createCompany(Long userId) throws Exception {
        return idOf(mockMvc.perform(post("/companies").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(COMPANY))
                .andExpect(status().isCreated()).andReturn());
    }

    private ResultActions list(Long userId, Long companyId) throws Exception {
        return mockMvc.perform(get("/companies/{id}/expense-categories", companyId).with(asUser(userId)));
    }

    Long idByName(Long userId, Long companyId, String name) throws Exception {
        String body = list(userId, companyId).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.parse(body).read("$[?(@.name == '" + name + "')].id");
        return ids.getFirst().longValue();
    }
}
