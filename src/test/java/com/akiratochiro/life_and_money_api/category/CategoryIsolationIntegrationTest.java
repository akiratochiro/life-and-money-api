package com.akiratochiro.life_and_money_api.category;

import com.akiratochiro.life_and_money_api.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "jwt.secret=46N3Mn7ipRBtI+LDENQDLRM2mXOGfeamGMqAcgFS9Gc=")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CategoryIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aUserCannotArchiveOrSeeAnotherUsersCategory() throws Exception {
        // given
        Long ana = register("ana@isolation.test");
        Long bruno = register("bruno@isolation.test");
        Long anasCategory = createCategory(ana, "Academia");

        // when / then: o Bruno tenta arquivar a categoria da Ana
        mockMvc.perform(post("/categories/{id}/archive", anasCategory).with(asUser(bruno)))
                .andExpect(status().isNotFound());

        // e não a vê na lista dele
        mockMvc.perform(get("/categories").with(asUser(bruno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(anasCategory.intValue()))));
    }

    private Long register(String email) throws Exception {
        String body = """
                {"name": "Teste", "email": "%s", "password": "senha12345"}
                """.formatted(email);

        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private Long createCategory(Long userId, String name) throws Exception {
        String body = """
                {"name": "%s", "type": "EXPENSE"}
                """.formatted(name);

        MvcResult result = mockMvc.perform(post("/categories")
                        .with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private static RequestPostProcessor asUser(Long userId) {
        return jwt().jwt(token -> token.subject(String.valueOf(userId)));
    }
}
