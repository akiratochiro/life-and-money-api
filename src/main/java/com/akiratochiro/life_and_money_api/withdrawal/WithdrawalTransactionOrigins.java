package com.akiratochiro.life_and_money_api.withdrawal;

import com.akiratochiro.life_and_money_api.transaction.TransactionOrigin;
import com.akiratochiro.life_and_money_api.transaction.TransactionOrigins;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/** As transações lançadas por retiradas, para o extrato (inversão de dependência, decisão 3). */
@Component
class WithdrawalTransactionOrigins implements TransactionOrigins {

    private final WithdrawalService service;

    WithdrawalTransactionOrigins(WithdrawalService service) {
        this.service = service;
    }

    @Override
    public Map<Long, TransactionOrigin> originsOf(Long userId, Collection<Long> transactionIds) {
        return service.originsOf(userId, transactionIds);
    }
}
