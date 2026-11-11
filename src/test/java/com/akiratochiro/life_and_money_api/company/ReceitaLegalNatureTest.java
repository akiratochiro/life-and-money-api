package com.akiratochiro.life_and_money_api.company;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceitaLegalNatureTest {

    @Test
    void anIndividualEntrepreneurIsAMeiOnlyWhenTheReceitaSaysSo() {
        assertThat(ReceitaLegalNature.from(2135, true, 0)).isEqualTo(LegalNature.MEI);
        assertThat(ReceitaLegalNature.from(2135, false, 0)).isEqualTo(LegalNature.EI);
        assertThat(ReceitaLegalNature.from(2135, null, null)).isEqualTo(LegalNature.EI);
    }

    @Test
    void aLimitedCompanyIsAnSluWithOnePartnerAndAnLtdaWithMore() {
        assertThat(ReceitaLegalNature.from(2062, null, 1)).isEqualTo(LegalNature.SLU);
        assertThat(ReceitaLegalNature.from(2062, null, 3)).isEqualTo(LegalNature.LTDA);
    }

    /** Sem o quadro societário não dá para separar SLU de LTDA: melhor não preencher do que errar. */
    @Test
    void aLimitedCompanyWithoutItsPartnersIsLeftForTheUser() {
        assertThat(ReceitaLegalNature.from(2062, null, null)).isNull();
        assertThat(ReceitaLegalNature.from(2062, null, 0)).isNull();
    }

    @Test
    void theOldEireliIsNowAnSluAndBothSimpleSocietiesMapToTheSame() {
        assertThat(ReceitaLegalNature.from(2305, null, 1)).isEqualTo(LegalNature.SLU);
        assertThat(ReceitaLegalNature.from(2232, null, 2)).isEqualTo(LegalNature.SOCIEDADE_SIMPLES);
        assertThat(ReceitaLegalNature.from(2240, null, 2)).isEqualTo(LegalNature.SOCIEDADE_SIMPLES);
    }

    @Test
    void anyOtherNatureIsLeftForTheUser() {
        assertThat(ReceitaLegalNature.from(3999, null, 5)).isNull(); // associação privada
        assertThat(ReceitaLegalNature.from(2046, null, 5)).isNull(); // sociedade anônima aberta
        assertThat(ReceitaLegalNature.from(null, null, null)).isNull();
    }
}
