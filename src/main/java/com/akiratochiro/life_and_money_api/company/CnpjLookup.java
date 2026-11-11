package com.akiratochiro.life_and_money_api.company;

import java.util.Optional;

/**
 * Consulta os dados públicos de um CNPJ. Em produção é a BrasilAPI; nos testes, uma lista fixa,
 * como os feriados.
 */
public interface CnpjLookup {

    /**
     * @return vazio quando o serviço respondeu que não conhece o CNPJ
     * @throws CnpjLookupException quando o serviço não respondeu direito (fora do ar, lento, corpo inválido)
     */
    Optional<CnpjData> find(Cnpj cnpj);
}
