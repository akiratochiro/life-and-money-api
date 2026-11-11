package com.akiratochiro.life_and_money_api.company;

/**
 * As combinações de enquadramento que a lei permite. Função pura, sem Spring nem banco: é testada
 * sozinha, e o banco repete as mesmas regras em {@code CHECK} como última barreira.
 *
 * <ul>
 *   <li>MEI é sempre SIMEI, e o SIMEI é só do MEI;</li>
 *   <li>o Anexo só existe no Simples Nacional, e ali é obrigatório;</li>
 *   <li>a atividade só existe no MEI, e ali é obrigatória, porque define o DAS-MEI.</li>
 * </ul>
 */
public final class RegimeRules {

    private RegimeRules() {
    }

    /** @throws InvalidCompanyDataException com o campo e a mensagem do primeiro problema */
    public static void validate(LegalNature legalNature, TaxRegime taxRegime,
                                AnnexConfig annexConfig, MeiActivity meiActivity) {
        if (legalNature == null) {
            throw new InvalidCompanyDataException("legalNature", "Escolha a natureza jurídica.");
        }
        if (taxRegime == null) {
            throw new InvalidCompanyDataException("taxRegime", "Escolha o regime tributário.");
        }

        boolean mei = legalNature == LegalNature.MEI;
        if (mei && taxRegime != TaxRegime.SIMEI) {
            throw new InvalidCompanyDataException("taxRegime", "O MEI é sempre tributado pelo SIMEI.");
        }
        if (!mei && taxRegime == TaxRegime.SIMEI) {
            throw new InvalidCompanyDataException("taxRegime", "O SIMEI é exclusivo do MEI.");
        }

        boolean simples = taxRegime == TaxRegime.SIMPLES_NACIONAL;
        if (simples && annexConfig == null) {
            throw new InvalidCompanyDataException("annexConfig",
                    "Escolha como o Anexo é definido: Anexo III fixo ou pelo Fator R.");
        }
        if (!simples && annexConfig != null) {
            throw new InvalidCompanyDataException("annexConfig",
                    "A configuração de Anexo só existe no Simples Nacional.");
        }

        if (mei && meiActivity == null) {
            throw new InvalidCompanyDataException("meiActivity",
                    "Informe a atividade do MEI: serviços, comércio ou indústria, ou ambos.");
        }
        if (!mei && meiActivity != null) {
            throw new InvalidCompanyDataException("meiActivity", "A atividade só é informada para MEI.");
        }
    }
}
