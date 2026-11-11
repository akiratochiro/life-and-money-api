package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.company.CompanyCreatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/** As obrigações do sistema com que toda empresa nasce. As mesmas que a V20 deu às que já existiam. */
@Component
class SystemObligations {

    private record Definition(ObligationKind kind, String name, Recurrence recurrence, int dueDay, Integer dueMonth, DueRule rule) {
    }

    static final List<Definition> ALL = List.of(
            new Definition(ObligationKind.DAS, "DAS do Simples Nacional", Recurrence.MONTHLY, 20, null, DueRule.POSTPONE),
            new Definition(ObligationKind.DAS_MEI, "DAS-MEI", Recurrence.MONTHLY, 20, null, DueRule.POSTPONE),
            new Definition(ObligationKind.DARF_PREVIDENCIARIO, "DARF do pró-labore (INSS e IRRF)", Recurrence.MONTHLY, 20, null,
                    DueRule.ANTICIPATE),
            new Definition(ObligationKind.DEFIS, "DEFIS (declaração anual do Simples)", Recurrence.YEARLY, 31, 3, DueRule.NONE),
            new Definition(ObligationKind.DASN_SIMEI, "DASN-SIMEI (declaração anual do MEI)", Recurrence.YEARLY, 31, 5, DueRule.NONE));

    private final TaxObligationRepository repository;

    SystemObligations(TaxObligationRepository repository) {
        this.repository = repository;
    }

    @EventListener
    void onCompanyCreated(CompanyCreatedEvent event) {
        repository.saveAll(ALL.stream()
                .map(d -> new TaxObligation(event.userId(), event.companyId(), d.kind(), d.name(), d.recurrence(), d.dueDay(),
                        d.dueMonth(), d.rule(), null, null, null))
                .toList());
    }
}
