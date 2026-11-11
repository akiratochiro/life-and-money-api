package com.akiratochiro.life_and_money_api.company;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Empresa de outro usuário responde 404 em toda rota, nunca 403: não revela que existe. */
class CompanyIsolationIntegrationTest extends IntegrationTest {

    @Test
    void noRouteReachesAnotherUsersCompany() throws Exception {
        Long ana = register("isolamento.empresa.ana@test.dev");
        Long bruno = register("isolamento.empresa.bruno@test.dev");
        Long anas = idOf(mockMvc.perform(post("/companies").with(asUser(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CompanyIntegrationTest.MEI.formatted("Da Ana")))
                .andExpect(status().isCreated())
                .andReturn());

        for (RequestBuilder request : new RequestBuilder[]{
                get("/companies/{id}", anas).with(asUser(bruno)),
                patch("/companies/{id}", anas).with(asUser(bruno))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Roubada\"}"),
                post("/companies/{id}/archive", anas).with(asUser(bruno)),
                post("/companies/{id}/unarchive", anas).with(asUser(bruno)),
        }) {
            mockMvc.perform(request)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Empresa não encontrada."));
        }

        mockMvc.perform(get("/companies").param("includeArchived", "true").with(asUser(bruno)))
                .andExpect(jsonPath("$[*].id", not(hasItem(anas.intValue()))));
        mockMvc.perform(get("/companies/{id}", anas).with(asUser(ana)))
                .andExpect(jsonPath("$.name").value("Da Ana"));
    }
}
