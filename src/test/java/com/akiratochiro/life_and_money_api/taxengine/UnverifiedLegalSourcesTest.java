package com.akiratochiro.life_and_money_api.taxengine;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lembrete antes do deploy: falha enquanto houver regra do motor lida só em compilação não oficial.
 * Fica fora da suíte comum (tag {@code pre-deploy}) e roda com {@code ./mvnw test -Dgroups=pre-deploy -Dsurefire.excludedGroups=}
 * antes do push (ver CLAUDE.md).
 */
@Tag("pre-deploy")
class UnverifiedLegalSourcesTest {

    @Test
    void everyTaxRuleWasCheckedAgainstTheOfficialText() {
        assertThat(Arrays.stream(UnverifiedLegalSource.values()).map(s -> s.name() + ": " + s.rule()).toList())
                .as("regras ainda não conferidas no texto oficial")
                .isEmpty();
    }
}
