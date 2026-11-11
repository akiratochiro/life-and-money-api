package com.akiratochiro.life_and_money_api.company;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CnpjTest {

    @Test
    void aNumericCnpjIsAcceptedWithOrWithoutPunctuation() {
        assertThat(Cnpj.parse("11.222.333/0001-81").value()).isEqualTo("11222333000181");
        assertThat(Cnpj.parse("11222333000181").value()).isEqualTo("11222333000181");
        assertThat(Cnpj.parse(" 11 222 333 0001 81 ").value()).isEqualTo("11222333000181");
    }

    /** O exemplo da própria Receita para o CNPJ alfanumérico: 12.ABC.345/01DE-35. */
    @Test
    void theAlphanumericExampleOfTheReceitaIsAccepted() {
        assertThat(Cnpj.parse("12.ABC.345/01DE-35").value()).isEqualTo("12ABC34501DE35");
    }

    @Test
    void lowercaseLettersAreNormalized() {
        assertThat(Cnpj.parse("12.abc.345/01de-35").value()).isEqualTo("12ABC34501DE35");
    }

    @Test
    void theCheckDigitsUseAsciiMinus48() {
        // "A" vale 17 e "Z" vale 42: o primeiro DV do exemplo da Receita é 3, o segundo é 5.
        assertThat(Cnpj.checkDigit("12ABC34501DE")).isEqualTo(3);
        assertThat(Cnpj.checkDigit("12ABC34501DE3")).isEqualTo(5);
    }

    @Test
    void itIsFormattedTheWayItIsPrinted() {
        assertThat(Cnpj.parse("12ABC34501DE35").formatted()).isEqualTo("12.ABC.345/01DE-35");
        assertThat(Cnpj.parse("11222333000181").formatted()).isEqualTo("11.222.333/0001-81");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "11.222.333/0001-82",   // segundo DV errado
            "11.222.333/0001-91",   // primeiro DV errado
            "12.ABC.345/01DE-3A",   // DV com letra: os verificadores são sempre dígitos
            "1122233300018",        // 13 caracteres
            "112223330001811",      // 15 caracteres
            "00.000.000/0000-00",   // passa no módulo 11, mas não é CNPJ
            "11.222.333/0001-8!",
            ""
    })
    void invalidValuesAreRejectedWithAMessageForTheField(String raw) {
        assertThatThrownBy(() -> Cnpj.parse(raw))
                .isInstanceOf(InvalidCompanyDataException.class)
                .hasMessage("CNPJ inválido. Confira os números e as letras.")
                .extracting("field").isEqualTo("cnpj");
    }
}
