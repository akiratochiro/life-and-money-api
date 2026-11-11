package com.akiratochiro.life_and_money_api.taxparam;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import com.akiratochiro.life_and_money_api.taxengine.Tables2026;
import com.akiratochiro.life_and_money_api.taxengine.TaxParameter;
import com.akiratochiro.life_and_money_api.taxengine.TaxTable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaxParameterIntegrationTest extends IntegrationTest {

    @Autowired
    private TaxParameterService service;

    /**
     * A semente (V18) é exatamente a tabela que os testes do motor usam: os exemplos feitos à mão
     * valem para o que está no banco, e um erro de digitação na migration aparece aqui.
     */
    @Test
    void theSeededTableIsTheOneTheEngineWasTestedWith() {
        TaxTable fromDatabase = service.tableFor(YearMonth.of(2026, 11));
        TaxTable byHand = Tables2026.table();

        for (TaxParameter key : TaxParameter.values()) {
            assertThat(fromDatabase.get(key)).as(key.name()).isEqualByComparingTo(byHand.get(key));
        }
        assertThat(fromDatabase.simplesBrackets()).hasSize(12);
        for (int i = 0; i < 12; i++) {
            var db = fromDatabase.simplesBrackets().get(i);
            var hand = byHand.simplesBrackets().get(i);
            assertThat(db.annex()).isEqualTo(hand.annex());
            assertThat(db.bracket()).isEqualTo(hand.bracket());
            assertThat(db.revenueUpTo()).isEqualByComparingTo(hand.revenueUpTo());
            assertThat(db.nominalRate()).isEqualByComparingTo(hand.nominalRate());
            assertThat(db.deduction()).isEqualByComparingTo(hand.deduction());
            assertThat(db.issShare()).isEqualByComparingTo(hand.issShare());
            assertThat(db.cppShare()).isEqualByComparingTo(hand.cppShare());
        }
        assertThat(fromDatabase.irrfBrackets()).hasSize(5);
        for (int i = 0; i < 5; i++) {
            var db = fromDatabase.irrfBrackets().get(i);
            var hand = byHand.irrfBrackets().get(i);
            assertThat(db.rate()).isEqualByComparingTo(hand.rate());
            assertThat(db.deduction()).isEqualByComparingTo(hand.deduction());
            if (hand.incomeUpTo() == null) {
                assertThat(db.incomeUpTo()).isNull();
            } else {
                assertThat(db.incomeUpTo()).isEqualByComparingTo(hand.incomeUpTo());
            }
        }
    }

    @Test
    void theMeiLimitFollowsTheLawForEachYear() {
        assertThat(service.tableFor(YearMonth.of(2026, 12)).get(TaxParameter.MEI_ANNUAL_LIMIT)).isEqualByComparingTo("81000");
        assertThat(service.tableFor(YearMonth.of(2027, 3)).get(TaxParameter.MEI_ANNUAL_LIMIT)).isEqualByComparingTo("110000");
        assertThat(service.tableFor(YearMonth.of(2028, 1)).get(TaxParameter.MEI_MONTHLY_LIMIT)).isEqualByComparingTo("11666.67");
    }

    @Test
    void competencesBefore2026AreNotCalculated() {
        assertThatThrownBy(() -> service.tableFor(YearMonth.of(2025, 12)))
                .isInstanceOf(TaxParametersUnavailableException.class)
                .hasMessage("O app calcula impostos a partir de 01/2026. Para 12/2025, registre o valor que foi pago.");
    }

    @Test
    void theEndpointShowsEachValueWithItsLegalSource() throws Exception {
        Long ana = register("parametros@test.dev");

        mockMvc.perform(get("/tax-parameters").param("date", "2026-11-11").with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parameters[?(@.key == 'MINIMUM_WAGE')].value").value(1621.0))
                .andExpect(jsonPath("$.parameters[?(@.key == 'MINIMUM_WAGE')].source").value("Decreto 12.797, de 23/12/2025, art. 1º"))
                .andExpect(jsonPath("$.simplesBrackets.length()").value(12))
                .andExpect(jsonPath("$.irrfBrackets.length()").value(5));
        mockMvc.perform(get("/tax-parameters").param("date", "2026-11-11")).andExpect(status().isUnauthorized());
    }
}
