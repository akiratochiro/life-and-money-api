package com.akiratochiro.life_and_money_api.invoice;

import com.akiratochiro.life_and_money_api.company.OpeningDateLock;
import org.springframework.stereotype.Component;

/**
 * Com qualquer nota na empresa, a data de abertura trava: a competência das notas é conferida
 * contra ela, e mudar a abertura depois poderia deixar notas antes da empresa existir.
 */
@Component
class InvoiceOpeningDateLock implements OpeningDateLock {

    private final InvoiceRepository repository;

    InvoiceOpeningDateLock(InvoiceRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean locksOpeningDate(Long companyId) {
        return repository.existsByCompanyId(companyId);
    }
}
