package com.akiratochiro.life_and_money_api.category;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryIsolationIntegrationTest extends IntegrationTest {

    @Test
    void aUserCannotArchiveOrSeeAnotherUsersCategory() throws Exception {
        // given
        Long ana = register("ana@isolation.test");
        Long bruno = register("bruno@isolation.test");
        Long anasCategory = createCategory(ana, "Academia", "EXPENSE");

        // when / then: o Bruno tenta arquivar a categoria da Ana
        mockMvc.perform(post("/categories/{id}/archive", anasCategory).with(asUser(bruno)))
                .andExpect(status().isNotFound());

        // e não a vê na lista dele
        mockMvc.perform(get("/categories").with(asUser(bruno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(anasCategory.intValue()))));
    }
}
