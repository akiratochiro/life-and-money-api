package com.akiratochiro.life_and_money_api.companyexpense;

import com.akiratochiro.life_and_money_api.category.CategoryColor;
import com.akiratochiro.life_and_money_api.category.CategoryIcon;
import com.akiratochiro.life_and_money_api.company.CompanyCreatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/** As categorias com que toda empresa nasce. As mesmas que a migration V15 deu às empresas que já existiam. */
@Component
class DefaultExpenseCategories {

    private static final List<Default> DEFAULTS = List.of(
            new Default("Aluguel", CategoryColor.PEACH, CategoryIcon.HOME, false, null),
            new Default("Equipamentos", CategoryColor.SKY, CategoryIcon.CART, false, null),
            new Default("Contador", CategoryColor.LAVENDER, CategoryIcon.BILLS, false, null),
            new Default("Software", CategoryColor.TEAL, CategoryIcon.PHONE, false, null),
            new Default("Salários e encargos", CategoryColor.MINT, CategoryIcon.SALARY, true, null),
            new Default("Outros", CategoryColor.SAND, CategoryIcon.OTHER, false, null),
            new Default("Impostos", CategoryColor.AMBER, CategoryIcon.COIN, false, ExpenseCategorySystemKey.TAXES)
    );

    private final CompanyExpenseCategoryRepository repository;

    DefaultExpenseCategories(CompanyExpenseCategoryRepository repository) {
        this.repository = repository;
    }

    @EventListener
    void onCompanyCreated(CompanyCreatedEvent event) {
        repository.saveAll(DEFAULTS.stream()
                .map(d -> new CompanyExpenseCategory(event.userId(), event.companyId(), d.name(), d.color(), d.icon(),
                        d.payroll(), d.systemKey()))
                .toList());
    }

    private record Default(String name, CategoryColor color, CategoryIcon icon, boolean payroll,
                           ExpenseCategorySystemKey systemKey) {
    }
}
