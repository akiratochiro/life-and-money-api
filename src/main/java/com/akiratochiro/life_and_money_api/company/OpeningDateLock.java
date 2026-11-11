package com.akiratochiro.life_and_money_api.company;

/**
 * Algo guardado numa empresa que depende da data de abertura e por isso a trava. A primeira
 * vigência do enquadramento começa no mês da abertura, e o {@code company} já confere sozinho se
 * houve mudança de enquadramento; as outras features (as notas fiscais, a partir da Fase 2)
 * implementam esta interface.
 *
 * <p>É inversão de dependência, como o {@code GoalOwnershipChecker} (decisão 3): o {@code invoice}
 * depende do {@code company}, então o {@code company} não pode chamar o {@code invoice}. Em vez
 * disso, ele declara a pergunta, e quem sabe responder se registra como bean.
 */
public interface OpeningDateLock {

    /** {@code true} se a empresa tem algo que impede mudar a data de abertura. */
    boolean locksOpeningDate(Long companyId);
}
