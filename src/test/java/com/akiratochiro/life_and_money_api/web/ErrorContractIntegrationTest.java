package com.akiratochiro.life_and_money_api.web;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O {@code detail} destas respostas é o texto que o front mostra para o usuário, então o
 * contrato de erro é testado como qualquer outra regra: status certo e mensagem em português.
 */
class ErrorContractIntegrationTest extends IntegrationTest {

    @Test
    void invalidRegistrationReturnsOneMessagePerFieldInPortuguese() throws Exception {
        String body = """
                {"name": "", "email": "nao-e-email", "password": "123"}
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.errors.name").value("Informe seu nome."))
                .andExpect(jsonPath("$.errors.email").value("Informe um e-mail válido."))
                .andExpect(jsonPath("$.errors.password")
                        .value("A senha deve ter entre 8 e 72 caracteres."));
    }

    @Test
    void wrongPasswordReturnsUnauthorizedWithoutSayingWhichFieldFailed() throws Exception {
        register("login.errado@test.dev");

        String body = """
                {"email": "login.errado@test.dev", "password": "senha-errada"}
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void repeatedCategoryNameReturnsConflictNamingTheCategory() throws Exception {
        Long user = register("categoria.repetida@test.dev");
        createCategory(user, "Academia", "EXPENSE");

        String body = """
                {"name": "Academia", "type": "EXPENSE"}
                """;

        mockMvc.perform(post("/categories")
                        .with(asUser(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Você já tem uma categoria chamada \"Academia\"."));
    }

    @Test
    void unknownCategoryReturnsNotFound() throws Exception {
        Long user = register("categoria.inexistente@test.dev");

        mockMvc.perform(post("/categories/{id}/archive", 999_999).with(asUser(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Categoria não encontrada."));
    }

    @Test
    void budgetOnAnIncomeCategoryIsRejectedWithAnExplanation() throws Exception {
        Long user = register("orcamento.receita@test.dev");
        Long salary = createCategory(user, "Bônus", "INCOME");

        String body = """
                {"mode": "AMOUNT", "limitValue": 100.00}
                """;

        mockMvc.perform(put("/budgets/{categoryId}", salary)
                        .with(asUser(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Categorias de receita não entram no orçamento."));
    }

    @Test
    void percentageAboveOneHundredIsRejectedBeforeReachingTheService() throws Exception {
        Long user = register("percentual.invalido@test.dev");
        Long market = createCategory(user, "Feira", "EXPENSE");

        String body = """
                {"mode": "PERCENTAGE", "limitValue": 120}
                """;

        mockMvc.perform(put("/budgets/{categoryId}", market)
                        .with(asUser(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.percentageWithinLimit")
                        .value("O percentual não pode passar de 100."));
    }

    @Test
    void linkingAGoalToAnExpenseCategoryReturnsUnprocessableInsteadOfServerError() throws Exception {
        Long user = register("meta.categoria@test.dev");
        Long rent = createCategory(user, "Aluguel", "EXPENSE");
        Long goalId = createGoal(user);

        String body = """
                {"categoryId": %d, "goalId": %d, "amount": 50.00, "transactionDate": "%s"}
                """.formatted(rent, goalId, LocalDate.now());

        mockMvc.perform(post("/transactions")
                        .with(asUser(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("Só é possível ligar uma transação a uma meta em categorias de poupança."));
    }

    @Test
    void unknownGoalReturnsNotFoundInsteadOfServerError() throws Exception {
        Long user = register("meta.inexistente@test.dev");
        Long investments = createCategory(user, "Reserva", "SAVING");

        String body = """
                {"categoryId": %d, "goalId": 999999, "amount": 50.00, "transactionDate": "%s"}
                """.formatted(investments, LocalDate.now());

        mockMvc.perform(post("/transactions")
                        .with(asUser(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Meta não encontrada."));
    }

    @Test
    void protectedEndpointsRejectRequestsWithoutAToken() throws Exception {
        mockMvc.perform(get("/categories"))
                .andExpect(status().isUnauthorized());
    }

    private Long createGoal(Long userId) throws Exception {
        String body = """
                {"name": "Viagem", "targetAmount": 5000.00, "deadline": "%s"}
                """.formatted(LocalDate.now().plusYears(1));

        return idOf(mockMvc.perform(post("/goals")
                        .with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn());
    }
}
