package com.akiratochiro.life_and_money_api.company;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.akiratochiro.life_and_money_api.company.AnnexConfig.ANEXO_III_FIXO;
import static com.akiratochiro.life_and_money_api.company.AnnexConfig.FATOR_R;
import static com.akiratochiro.life_and_money_api.company.LegalNature.MEI;
import static com.akiratochiro.life_and_money_api.company.MeiActivity.SERVICOS;
import static com.akiratochiro.life_and_money_api.company.TaxRegime.LUCRO_PRESUMIDO;
import static com.akiratochiro.life_and_money_api.company.TaxRegime.LUCRO_REAL;
import static com.akiratochiro.life_and_money_api.company.TaxRegime.SIMEI;
import static com.akiratochiro.life_and_money_api.company.TaxRegime.SIMPLES_NACIONAL;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegimeRulesTest {

    @Test
    void aMeiInTheSimeiWithAnActivityIsValid() {
        assertThatCode(() -> RegimeRules.validate(MEI, SIMEI, null, SERVICOS)).doesNotThrowAnyException();
    }

    /** Toda natureza que não é MEI pode estar no Simples, no Presumido ou no Real. */
    @ParameterizedTest
    @EnumSource(value = LegalNature.class, names = "MEI", mode = EnumSource.Mode.EXCLUDE)
    void everyOtherNatureAcceptsSimplesPresumidoAndReal(LegalNature nature) {
        assertThatCode(() -> RegimeRules.validate(nature, SIMPLES_NACIONAL, FATOR_R, null)).doesNotThrowAnyException();
        assertThatCode(() -> RegimeRules.validate(nature, SIMPLES_NACIONAL, ANEXO_III_FIXO, null)).doesNotThrowAnyException();
        assertThatCode(() -> RegimeRules.validate(nature, LUCRO_PRESUMIDO, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> RegimeRules.validate(nature, LUCRO_REAL, null, null)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = TaxRegime.class, names = "SIMEI", mode = EnumSource.Mode.EXCLUDE)
    void aMeiOutsideTheSimeiIsRejected(TaxRegime regime) {
        AnnexConfig annex = regime == SIMPLES_NACIONAL ? FATOR_R : null;

        assertRejected(() -> RegimeRules.validate(MEI, regime, annex, SERVICOS),
                "taxRegime", "O MEI é sempre tributado pelo SIMEI.");
    }

    @ParameterizedTest
    @EnumSource(value = LegalNature.class, names = "MEI", mode = EnumSource.Mode.EXCLUDE)
    void theSimeiIsOnlyForMei(LegalNature nature) {
        assertRejected(() -> RegimeRules.validate(nature, SIMEI, null, null),
                "taxRegime", "O SIMEI é exclusivo do MEI.");
    }

    @Test
    void theSimplesRequiresAnAnnexConfig() {
        assertRejected(() -> RegimeRules.validate(LegalNature.SLU, SIMPLES_NACIONAL, null, null),
                "annexConfig", "Escolha como o Anexo é definido: Anexo III fixo ou pelo Fator R.");
    }

    @Test
    void anAnnexConfigOutsideTheSimplesIsRejected() {
        assertRejected(() -> RegimeRules.validate(LegalNature.LTDA, LUCRO_PRESUMIDO, FATOR_R, null),
                "annexConfig", "A configuração de Anexo só existe no Simples Nacional.");
        assertRejected(() -> RegimeRules.validate(MEI, SIMEI, FATOR_R, SERVICOS),
                "annexConfig", "A configuração de Anexo só existe no Simples Nacional.");
    }

    @Test
    void aMeiRequiresItsActivity() {
        assertRejected(() -> RegimeRules.validate(MEI, SIMEI, null, null),
                "meiActivity", "Informe a atividade do MEI: serviços, comércio ou indústria, ou ambos.");
    }

    @Test
    void anActivityOutsideTheMeiIsRejected() {
        assertRejected(() -> RegimeRules.validate(LegalNature.EI, SIMPLES_NACIONAL, FATOR_R, SERVICOS),
                "meiActivity", "A atividade só é informada para MEI.");
    }

    @Test
    void natureAndRegimeAreRequired() {
        assertRejected(() -> RegimeRules.validate(null, SIMEI, null, SERVICOS),
                "legalNature", "Escolha a natureza jurídica.");
        assertRejected(() -> RegimeRules.validate(MEI, null, null, SERVICOS),
                "taxRegime", "Escolha o regime tributário.");
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                                       String field, String message) {
        assertThatThrownBy(call)
                .isInstanceOf(InvalidCompanyDataException.class)
                .hasMessage(message)
                .extracting("field").isEqualTo(field);
    }
}
