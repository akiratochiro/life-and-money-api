package com.akiratochiro.life_and_money_api.company;

import java.time.LocalDate;

/**
 * O que a consulta de CNPJ sabe preencher. Tudo pode faltar: o formulário usa o que veio e deixa o
 * resto para o usuário.
 *
 * @param legalNature só quando o código da Receita leva a uma natureza sem ambiguidade
 */
public record CnpjData(String name, String tradeName, LocalDate openingDate, LegalNature legalNature) {
}
