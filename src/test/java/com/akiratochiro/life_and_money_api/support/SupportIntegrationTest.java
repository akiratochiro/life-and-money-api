package com.akiratochiro.life_and_money_api.support;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.akiratochiro.life_and_money_api.email.EmailMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O contato com o suporte (decisão 34): vai para o endereço da configuração, respondendo para a conta. */
class SupportIntegrationTest extends IntegrationTest {

    static final String SUPPORT = "suporte@test.dev";

    @AfterEach
    void clearSupportInbox() {
        emails.clearTo(SUPPORT);
    }

    @Test
    void theMessageGoesToSupportAndRepliesGoToTheUser() throws Exception {
        Long ana = register("suporte.ana@test.dev");

        send(ana, """
                {"subject": "Dúvida no DAS", "message": "O DAS de outubro veio diferente.\\nPode ser o ISS retido?\\n\\nObrigada!"}
                """).andExpect(status().isAccepted());

        EmailMessage message = emails.awaitMessageTo(SUPPORT, 1);
        assertThat(message.subject()).isEqualTo("[Suporte] Dúvida no DAS");
        assertThat(message.replyTo()).isEqualTo("suporte.ana@test.dev");
        assertThat(message.text()).contains("Mensagem de Teste (suporte.ana@test.dev), conta nº " + ana + ".",
                "O DAS de outubro veio diferente.\nPode ser o ISS retido?", "Obrigada!");
        assertThat(message.html()).contains("<p>O DAS de outubro veio diferente.<br>Pode ser o ISS retido?</p>");
    }

    /** O texto é de quem escreveu: no HTML, nada dele vira marcação. */
    @Test
    void theMessageIsEscapedAndTheSubjectHasADefault() throws Exception {
        Long ana = register("suporte.html@test.dev");

        send(ana, """
                {"message": "<script>alert(1)</script> ajuda, por favor"}
                """).andExpect(status().isAccepted());

        EmailMessage message = emails.awaitMessageTo(SUPPORT, 1);
        assertThat(message.subject()).isEqualTo("[Suporte] Mensagem pelo app");
        assertThat(message.html()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void theMessageIsValidated() throws Exception {
        Long ana = register("suporte.validacao@test.dev");

        send(ana, """
                {"message": "oi"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").value("A mensagem deve ter entre 10 e 3.000 caracteres."));
        assertThat(emails.messagesTo(SUPPORT)).isEmpty();
    }

    @Test
    void onlyLoggedInUsersCanWrite() throws Exception {
        mockMvc.perform(post("/support").contentType(MediaType.APPLICATION_JSON).content("{\"message\": \"mensagem de spam aqui\"}"))
                .andExpect(status().isUnauthorized());
    }

    ResultActions send(Long userId, String body) throws Exception {
        return mockMvc.perform(post("/support").with(asUser(userId)).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
