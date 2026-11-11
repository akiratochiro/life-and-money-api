package com.akiratochiro.life_and_money_api.company;

/**
 * Traduz o código de natureza jurídica da Receita (tabela da CONCLA) para a natureza do app. Só
 * devolve algo quando a tradução é inequívoca; nos outros casos, devolve {@code null} e o usuário
 * escolhe. Errar aqui seria pior do que não preencher, porque o enquadramento define os impostos.
 */
final class ReceitaLegalNature {

    /** 213-5: Empresário (Individual). É MEI ou EI, e quem diz é a opção pelo MEI. */
    static final int EMPRESARIO_INDIVIDUAL = 2135;
    /** 206-2: Sociedade Empresária Limitada. A SLU usa o mesmo código, com um sócio só. */
    static final int SOCIEDADE_EMPRESARIA_LIMITADA = 2062;
    /** 230-5: EIRELI de natureza empresária, transformada em SLU pela Lei 14.195/2021. */
    static final int EIRELI_EMPRESARIA = 2305;
    /** 223-2: Sociedade Simples Pura. */
    static final int SOCIEDADE_SIMPLES_PURA = 2232;
    /** 224-0: Sociedade Simples Limitada. */
    static final int SOCIEDADE_SIMPLES_LIMITADA = 2240;

    private ReceitaLegalNature() {
    }

    /**
     * @param code     o código sem hífen (2135 para 213-5)
     * @param mei      a opção pelo MEI; {@code null} quando a Receita não informa
     * @param partners quantos sócios o quadro societário lista; {@code null} quando não veio
     */
    static LegalNature from(Integer code, Boolean mei, Integer partners) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case EMPRESARIO_INDIVIDUAL -> Boolean.TRUE.equals(mei) ? LegalNature.MEI : LegalNature.EI;
            case SOCIEDADE_EMPRESARIA_LIMITADA -> partners == null || partners == 0
                    ? null
                    : partners == 1 ? LegalNature.SLU : LegalNature.LTDA;
            case EIRELI_EMPRESARIA -> LegalNature.SLU;
            case SOCIEDADE_SIMPLES_PURA, SOCIEDADE_SIMPLES_LIMITADA -> LegalNature.SOCIEDADE_SIMPLES;
            default -> null;
        };
    }
}
