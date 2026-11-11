package com.akiratochiro.life_and_money_api.invoice;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static com.akiratochiro.life_and_money_api.invoice.InvoiceTestSupport.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RevenueIntegrationTest extends IntegrationTest {

    @BeforeEach
    void fixToday() {
        clock.setTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    @Test
    void theRevenueIsTheActiveNotesByCompetenceMinusTheUnconditionalDiscount() throws Exception {
        Long ana = register("receita.mes@test.dev");
        Long company = createCompany(ana);
        // Outubro: 10.000 com 500 de desconto (conta 9.500). Novembro: a segunda nota, cancelada depois.
        saveXml(ana, company, xml().replace("<vServPrest><vServ>10000.00</vServ></vServPrest>",
                "<vServPrest><vServ>10000.00</vServ></vServPrest><vDescCondIncond><vDescIncond>500</vDescIncond></vDescCondIncond>"));
        Long cancelled = saveXml(ana, company, secondXml().replace("<dCompet>2026-10-31</dCompet>", "<dCompet>2026-11-05</dCompet>"));
        mockMvc.perform(post("/companies/{id}/invoices/{iid}/cancel", company, cancelled).with(asUser(ana)))
                .andExpect(status().isOk());

        revenue(ana, company, "2026-09-15", "2026-11-11", "MONTH")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("MONTH"))
                .andExpect(jsonPath("$.from").value("2026-09-01"))
                .andExpect(jsonPath("$.to").value("2026-11-30"))
                .andExpect(jsonPath("$.total").value(9500.00))
                .andExpect(jsonPath("$.points.length()").value(3))
                .andExpect(jsonPath("$.points[0].amount").value(0))
                .andExpect(jsonPath("$.points[1].start").value("2026-10-01"))
                .andExpect(jsonPath("$.points[1].amount").value(9500.00))
                .andExpect(jsonPath("$.points[1].invoices").value(1))
                .andExpect(jsonPath("$.points[2].invoices").value(0));
    }

    @Test
    void byWeekTheRangeGoesFromMondayToSunday() throws Exception {
        Long ana = register("receita.semana@test.dev");
        Long company = createCompany(ana);
        saveXml(ana, company, xml());

        // 31/10/2026 é sábado: a semana começa em 26/10.
        revenue(ana, company, "2026-10-28", "2026-11-03", "WEEK")
                .andExpect(jsonPath("$.from").value("2026-10-26"))
                .andExpect(jsonPath("$.to").value("2026-11-08"))
                .andExpect(jsonPath("$.points[0].end").value("2026-11-01"))
                .andExpect(jsonPath("$.points[0].amount").value(10000.00));
    }

    @Test
    void anInvalidRangeExplainsTheLimit() throws Exception {
        Long ana = register("receita.limite@test.dev");
        Long company = createCompany(ana);

        revenue(ana, company, "2026-01-01", "2026-12-31", "DAY")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Escolha um período de no máximo 93 dias."));
    }

    @Test
    void anotherUsersRevenueIsNotFound() throws Exception {
        Long ana = register("receita.isolamento.ana@test.dev");
        Long bruno = register("receita.isolamento.bruno@test.dev");
        Long company = createCompany(ana);

        revenue(bruno, company, "2026-10-01", "2026-10-31", "MONTH").andExpect(status().isNotFound());
    }

    private Long createCompany(Long userId) throws Exception {
        return idOf(mockMvc.perform(post("/companies").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(COMPANY.formatted(PROVIDER_CNPJ)))
                .andExpect(status().isCreated()).andReturn());
    }

    private Long saveXml(Long userId, Long companyId, String xml) throws Exception {
        return idOf(mockMvc.perform(multipart("/companies/{id}/invoices", companyId).file(xmlFile("file", xml)).with(asUser(userId)))
                .andExpect(status().isCreated()).andReturn());
    }

    private ResultActions revenue(Long userId, Long companyId, String from, String to, String granularity) throws Exception {
        return mockMvc.perform(get("/companies/{id}/revenue", companyId).with(asUser(userId))
                .param("from", from).param("to", to).param("granularity", granularity));
    }
}
