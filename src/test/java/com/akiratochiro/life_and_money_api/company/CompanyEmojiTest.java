package com.akiratochiro.life_and_money_api.company;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyEmojiTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "💼",
            "👩‍💻",        // mulher + ZWJ + computador: três code points, um emoji
            "👨‍👩‍👧",       // família, com dois ZWJ
            "🇧🇷",          // bandeira: duas letras regionais
            "👍🏽",         // com tom de pele
            "☕"
    })
    void oneEmojiIsAcceptedHoweverManyCodePointsItHas(String emoji) {
        assertThat(CompanyEmoji.validate(emoji)).isEqualTo(emoji);
    }

    @Test
    void surroundingSpacesAreRemoved() {
        assertThat(CompanyEmoji.validate(" 💼 ")).isEqualTo("💼");
    }

    @ParameterizedTest
    @ValueSource(strings = {"💼💼", "a", "1", "#", "💼a", "Empresa"})
    void textOrMoreThanOneEmojiIsRejected(String raw) {
        assertThatThrownBy(() -> CompanyEmoji.validate(raw))
                .isInstanceOf(InvalidCompanyDataException.class)
                .hasMessage("Use um único emoji.")
                .extracting("field").isEqualTo("emoji");
    }

    @Test
    void anEmptyEmojiAsksForOne() {
        assertThatThrownBy(() -> CompanyEmoji.validate("  "))
                .isInstanceOf(InvalidCompanyDataException.class)
                .hasMessage("Escolha um emoji para a empresa.");
    }
}
