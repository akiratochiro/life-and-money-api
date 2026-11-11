package com.akiratochiro.life_and_money_api.invoice;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

import java.time.Instant;

import static com.akiratochiro.life_and_money_api.invoice.InvoiceTestSupport.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nota de outro usuário, ou de outra empresa pelo caminho errado, responde 404 em toda rota. */
class InvoiceIsolationIntegrationTest extends IntegrationTest {

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    @Test
    void noRouteReachesAnotherUsersNotes() throws Exception {
        Long ana = register("isolamento.nota.ana@test.dev");
        Long bruno = register("isolamento.nota.bruno@test.dev");
        Long anasCompany = createCompany(ana);
        Long anasInvoice = idOf(mockMvc.perform(multipart("/companies/{id}/invoices", anasCompany)
                        .file(xmlFile("file", xml())).with(asUser(ana)))
                .andExpect(status().isCreated())
                .andReturn());

        for (RequestBuilder request : new RequestBuilder[]{
                get("/companies/{id}/invoices", anasCompany).param("year", "2026").with(asUser(bruno)),
                get("/companies/{id}/invoices/years", anasCompany).with(asUser(bruno)),
                get("/companies/{id}/invoices/{iid}", anasCompany, anasInvoice).with(asUser(bruno)),
                get("/companies/{id}/invoices/{iid}/file", anasCompany, anasInvoice).with(asUser(bruno)),
                post("/companies/{id}/invoices/{iid}/cancel", anasCompany, anasInvoice).with(asUser(bruno)),
                put("/companies/{id}/invoices/{iid}", anasCompany, anasInvoice).with(asUser(bruno))
                        .contentType(MediaType.APPLICATION_JSON).content(MANUAL_DATA),
                delete("/companies/{id}/invoices/{iid}", anasCompany, anasInvoice).with(asUser(bruno)),
                multipart("/companies/{id}/invoices/parse", anasCompany).file(xmlFile("files", xml())).with(asUser(bruno)),
                multipart("/companies/{id}/invoices", anasCompany).file(xmlFile("file", secondXml())).with(asUser(bruno)),
        }) {
            mockMvc.perform(request)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Empresa não encontrada."));
        }

        // A nota da Ana pelo caminho da empresa do Bruno: a empresa é dele, mas a nota não é dela.
        Long brunosCompany = createCompany(bruno);
        mockMvc.perform(get("/companies/{id}/invoices/{iid}/file", brunosCompany, anasInvoice).with(asUser(bruno)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Nota fiscal não encontrada."));

        // E a Ana continua com a nota dela intacta.
        mockMvc.perform(get("/companies/{id}/invoices/{iid}", anasCompany, anasInvoice).with(asUser(ana)))
                .andExpect(jsonPath("$.status").value("ATIVA"));
    }

    private Long createCompany(Long userId) throws Exception {
        return idOf(mockMvc.perform(post("/companies").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(COMPANY.formatted(PROVIDER_CNPJ)))
                .andExpect(status().isCreated())
                .andReturn());
    }
}
