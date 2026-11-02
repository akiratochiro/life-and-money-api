package com.akiratochiro.life_and_money_api.category;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryColorIconIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theDefaultCategoriesComeWithTheirColorAndIcon() throws Exception {
        Long user = register("cores.padrao@test.dev");

        mockMvc.perform(get("/categories").with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Moradia')].color").value("PEACH"))
                .andExpect(jsonPath("$[?(@.name == 'Moradia')].icon").value("HOME"))
                .andExpect(jsonPath("$[?(@.name == 'Salário')].icon").value("SALARY"))
                .andExpect(jsonPath("$[?(@.name == 'Investimentos')].icon").value("PIGGY_BANK"));
    }

    @Test
    void aCategoryCanBeCreatedWithColorAndIcon() throws Exception {
        Long user = register("cores.cria@test.dev");

        create(user, """
                {"name": "Pet", "type": "EXPENSE", "color": "AMBER", "icon": "PET"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.color").value("AMBER"))
                .andExpect(jsonPath("$.icon").value("PET"));
    }

    @Test
    void withoutColorAndIconTheTypeDefaultIsUsed() throws Exception {
        // É o que mantém funcionando um front que ainda não manda os campos novos.
        Long user = register("cores.sem@test.dev");

        create(user, """
                {"name": "Freela", "type": "INCOME"}
                """)
                .andExpect(jsonPath("$.color").value("MINT"))
                .andExpect(jsonPath("$.icon").value("COIN"));
        create(user, """
                {"name": "Farmácia", "type": "EXPENSE"}
                """)
                .andExpect(jsonPath("$.color").value("CORAL"))
                .andExpect(jsonPath("$.icon").value("OTHER"));
        create(user, """
                {"name": "Reserva", "type": "SAVING"}
                """)
                .andExpect(jsonPath("$.color").value("PINK"))
                .andExpect(jsonPath("$.icon").value("PIGGY_BANK"));
    }

    @Test
    void aColorOutsideThePaletteIsRejectedWithTheFieldMarked() throws Exception {
        Long user = register("cores.invalida@test.dev");

        create(user, """
                {"name": "Pet", "type": "EXPENSE", "color": "#ff00ff"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Confira os campos destacados e tente de novo."))
                .andExpect(jsonPath("$.errors.color").value("Escolha uma das opções disponíveis."));
    }

    @Test
    void anIconOutsideTheListIsRejectedWithTheFieldMarked() throws Exception {
        Long user = register("icone.invalido@test.dev");

        create(user, """
                {"name": "Pet", "type": "EXPENSE", "icon": "UNICORN"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.icon").value("Escolha uma das opções disponíveis."));
    }

    @Test
    void anInvalidTypeIsNowMarkedTooInPortuguese() throws Exception {
        Long user = register("tipo.invalido@test.dev");

        create(user, """
                {"name": "Pet", "type": "GASTO"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.errors.type").value("Escolha uma das opções disponíveis."));
    }

    @Test
    void patchingOnlyTheColorKeepsNameAndIcon() throws Exception {
        Long user = register("cores.patch@test.dev");
        Long pet = idOf(create(user, """
                {"name": "Pet", "type": "EXPENSE", "color": "AMBER", "icon": "PET"}
                """).andReturn());

        update(user, pet, """
                {"color": "TEAL"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pet"))
                .andExpect(jsonPath("$.color").value("TEAL"))
                .andExpect(jsonPath("$.icon").value("PET"));

        update(user, pet, """
                {"name": "Bichos", "icon": "HEART"}
                """)
                .andExpect(jsonPath("$.name").value("Bichos"))
                .andExpect(jsonPath("$.color").value("TEAL"))
                .andExpect(jsonPath("$.icon").value("HEART"));
    }

    @Test
    void renamingStillWorksTheOldWay() throws Exception {
        // O front em produção ainda manda só {"name": ...} até o próximo deploy.
        Long user = register("cores.renomeia@test.dev");
        Long pet = idOf(create(user, """
                {"name": "Pet", "type": "EXPENSE", "color": "AMBER", "icon": "PET"}
                """).andReturn());

        update(user, pet, """
                {"name": "Animais"}
                """)
                .andExpect(jsonPath("$.name").value("Animais"))
                .andExpect(jsonPath("$.color").value("AMBER"));
    }

    @Test
    void anEmptyPatchOrABlankNameIsRejected() throws Exception {
        Long user = register("cores.vazio@test.dev");
        Long pet = idOf(create(user, """
                {"name": "Pet", "type": "EXPENSE"}
                """).andReturn());

        update(user, pet, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.anyChange").value("Informe o que mudar: nome, cor ou ícone."));
        update(user, pet, """
                {"name": "   "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Informe o nome da categoria."));
    }

    @Test
    void anotherUsersCategoryIsNotFound() throws Exception {
        Long ana = register("cores.ana@test.dev");
        Long bia = register("cores.bia@test.dev");
        Long pet = idOf(create(ana, """
                {"name": "Pet", "type": "EXPENSE"}
                """).andReturn());

        update(bia, pet, """
                {"color": "TEAL"}
                """)
                .andExpect(status().isNotFound());
    }

    @Test
    void theDatabaseItselfRejectsAColorOutsideThePalette() {
        // A segunda barreira: mesmo um UPDATE direto no banco não grava uma cor que o app não desenha.
        assertThatThrownBy(() -> jdbc.update("update categories set color = 'NEON' where id = (select min(id) from categories)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ResultActions create(Long userId, String body) throws Exception {
        return mockMvc.perform(post("/categories").with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(Long userId, Long id, String body) throws Exception {
        return mockMvc.perform(patch("/categories/{id}", id).with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
