package com.akiratochiro.life_and_money_api.taxengine;

/**
 * Regras do motor cuja redação foi lida numa compilação não oficial, porque o texto oficial não
 * pôde ser aberto na pesquisa de 11/11/2026 (o site de normas da Receita só abre com JavaScript, e o
 * Planalto recusou o acesso automatizado). Cada uma precisa ser reconferida no texto oficial e,
 * conferida, sai daqui.
 *
 * <p>O {@code UnverifiedLegalSourcesTest} (tag {@code pre-deploy}) falha enquanto esta lista não
 * estiver vazia; ele roda antes do push (ver CLAUDE.md), não na suíte comum.
 */
public enum UnverifiedLegalSource {
    // As nove regras de 2026 (Res. CGSN 140, arts. 22, 25 §9º, 26 §§6º-7º, 40 §3º, 72, 109 e 145;
    // LC 123, art. 18-A §7º; Lei 9.250, art. 6º-A no Simples) foram conferidas no texto oficial em
    // 11/11/2026 (decisão 24). Regra nova lida só em compilação volta a entrar aqui.

    // Fase 10: a LC 123, art. 3º, §§10 a 12, foi conferida no Planalto em 12/11/2026 (o §12 só alcança o §10).
    ;

    private final String rule;

    UnverifiedLegalSource(String rule) {
        this.rule = rule;
    }

    public String rule() {
        return rule;
    }
}
