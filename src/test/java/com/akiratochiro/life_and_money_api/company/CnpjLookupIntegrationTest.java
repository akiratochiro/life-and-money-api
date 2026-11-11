package com.akiratochiro.life_and_money_api.company;

import com.akiratochiro.life_and_money_api.FixedCnpjLookup;
import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CnpjLookupIntegrationTest extends IntegrationTest {

    @Test
    void aKnownCnpjFillsWhatTheFormNeeds() throws Exception {
        Long ana = register("cnpj.encontrado@test.dev");

        // Sem a barra: num caminho de URL ela separa segmentos. O front manda só letras e dígitos.
        mockMvc.perform(get("/cnpj/{cnpj}", "11222333000181").with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.name").value("ANA SOUZA 12345678900"))
                .andExpect(jsonPath("$.tradeName").value("Ana Design"))
                .andExpect(jsonPath("$.openingDate").value("2024-03-10"))
                .andExpect(jsonPath("$.legalNature").value("MEI"));
    }

    @Test
    void anUnknownCnpjIsAnAnswerNotAnError() throws Exception {
        Long ana = register("cnpj.desconhecido@test.dev");

        mockMvc.perform(get("/cnpj/{cnpj}", "12ABC34501DE35").with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.name").value(nullValue()));
    }

    /** A BrasilAPI fora do ar nunca vira erro: o cadastro segue à mão. */
    @Test
    void withTheBrasilApiDownTheAnswerSaysUnavailable() throws Exception {
        Long ana = register("cnpj.fora@test.dev");
        cnpjs.failing(true);

        mockMvc.perform(get("/cnpj/{cnpj}", FixedCnpjLookup.KNOWN_MEI).with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.found").value(false));
    }

    @Test
    void anInvalidCnpjIsRejectedBeforeAnyLookup() throws Exception {
        Long ana = register("cnpj.invalido@test.dev");

        mockMvc.perform(get("/cnpj/{cnpj}", "11222333000182").with(asUser(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cnpj").value("CNPJ inválido. Confira os números e as letras."));
    }

    @Test
    void itRequiresASession() throws Exception {
        mockMvc.perform(get("/cnpj/{cnpj}", FixedCnpjLookup.KNOWN_MEI)).andExpect(status().isUnauthorized());
    }
}
