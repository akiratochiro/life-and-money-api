package com.akiratochiro.life_and_money_api.holiday;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HolidayIntegrationTest extends IntegrationTest {

    @Test
    void listsTheNationalHolidaysOfTheYear() throws Exception {
        Long user = register("feriados.lista@test.dev");

        mockMvc.perform(get("/holidays").param("year", "2026").with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.holidays.length()").value(14))
                .andExpect(jsonPath("$.holidays[1].date").value("2026-02-16"))
                .andExpect(jsonPath("$.holidays[1].name").value("Carnaval"));
    }

    @Test
    void withTheBrasilApiDownTheHolidaysAreCalculatedLocally() throws Exception {
        Long user = register("feriados.fora@test.dev");
        holidays.failing(true);

        mockMvc.perform(get("/holidays").param("year", "2026").with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.source").value("LOCAL"))
                .andExpect(jsonPath("$.holidays.length()").value(14))
                .andExpect(jsonPath("$.holidays[?(@.name == 'Finados')].date").value("2026-11-02"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/holidays").param("year", "2026"))
                .andExpect(status().isUnauthorized());
    }
}
