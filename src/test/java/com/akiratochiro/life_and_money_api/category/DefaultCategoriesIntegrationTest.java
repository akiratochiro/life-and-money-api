package com.akiratochiro.life_and_money_api.category;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DefaultCategoriesIntegrationTest extends IntegrationTest {

    @Test
    void newUsersStartWithTheDefaultCategories() throws Exception {
        // given
        Long user = register("default.categories@test.dev");

        // when / then
        mockMvc.perform(get("/categories").with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder(
                        "Salário", "Mercado", "Transporte", "Moradia", "Lazer", "Investimentos")));
    }
}
