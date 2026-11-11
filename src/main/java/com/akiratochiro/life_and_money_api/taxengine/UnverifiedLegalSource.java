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
    RES_CGSN_140_ART_22("RBT12 no início de atividade: 1º mês = receita do mês × 12; até o 12º, média × 12"),
    RES_CGSN_140_ART_25_PAR_9("ISS retido: a parte do ISS é desconsiderada no DAS daquela receita"),
    RES_CGSN_140_ART_26_PAR_6_7("Fator R no início: folha sem receita = 0,28; receita sem folha = 0,01"),
    RES_CGSN_140_ART_40_PAR_3("DAS e DAS-MEI sem expediente bancário no dia 20: próximo dia útil"),
    RES_CGSN_140_ART_72("DEFIS: até 31 de março do ano seguinte"),
    RES_CGSN_140_ART_109("DASN-SIMEI: até o último dia de maio"),
    LC_123_ART_18A_PAR_7("MEI: excesso de até 20% sai do MEI em janeiro seguinte; acima disso, retroativo");

    private final String rule;

    UnverifiedLegalSource(String rule) {
        this.rule = rule;
    }

    public String rule() {
        return rule;
    }
}
