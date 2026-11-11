package com.akiratochiro.life_and_money_api.transaction;

import java.util.Collection;
import java.util.Map;

/**
 * Quem cria transações por outra feature diz de onde cada uma veio. É uma inversão de dependência
 * (decisão 3): {@code withdrawal} depende de {@code transaction} para lançar, então {@code transaction}
 * não pode chamar {@code withdrawal}; ela só conhece esta interface.
 */
public interface TransactionOrigins {

    /** As origens das transações do usuário com esses ids; as que não vieram daqui ficam de fora. */
    Map<Long, TransactionOrigin> originsOf(Long userId, Collection<Long> transactionIds);
}
