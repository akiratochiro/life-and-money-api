package com.akiratochiro.life_and_money_api.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * O que foi lido de uma nota, antes de virar uma nota da empresa. Do XML vem tudo; do PDF, só o que
 * o texto deixou reconhecer, e o resto fica nulo para o usuário completar.
 *
 * @param providerDocument CNPJ (ou CPF) de quem emitiu, para conferir se a nota é da empresa
 * @param homologation     nota do ambiente de testes, sem valor fiscal
 */
public record ParsedInvoice(
        String number,
        String accessKey,
        LocalDate issueDate,
        LocalDate competenceDate,
        BigDecimal serviceAmount,
        BigDecimal unconditionalDiscountAmount,
        BigDecimal netAmount,
        BigDecimal issWithheldAmount,
        BigDecimal irrfWithheldAmount,
        BigDecimal pisWithheldAmount,
        BigDecimal cofinsWithheldAmount,
        BigDecimal csllWithheldAmount,
        BigDecimal inssWithheldAmount,
        String takerName,
        String takerDocument,
        String providerDocument,
        String replacedAccessKey,
        boolean homologation
) {
}
