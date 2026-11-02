package com.akiratochiro.life_and_money_api.category;

import com.akiratochiro.life_and_money_api.user.UserRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class DefaultCategoriesListener {

    /** Os mesmos pares que a migration V10 deu às categorias padrão que já existiam. */
    private static final List<DefaultCategory> DEFAULTS = List.of(
            new DefaultCategory("Salário", CategoryType.INCOME, CategoryColor.MINT, CategoryIcon.SALARY),
            new DefaultCategory("Mercado", CategoryType.EXPENSE, CategoryColor.LIME, CategoryIcon.CART),
            new DefaultCategory("Transporte", CategoryType.EXPENSE, CategoryColor.SKY, CategoryIcon.TRANSPORT),
            new DefaultCategory("Moradia", CategoryType.EXPENSE, CategoryColor.PEACH, CategoryIcon.HOME),
            new DefaultCategory("Lazer", CategoryType.EXPENSE, CategoryColor.LAVENDER, CategoryIcon.LEISURE),
            new DefaultCategory("Investimentos", CategoryType.SAVING, CategoryColor.PINK, CategoryIcon.PIGGY_BANK)
    );

    private final CategoryRepository categoryRepository;

    DefaultCategoriesListener(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @EventListener
    void onUserRegistered(UserRegisteredEvent event) {
        List<Category> categories = DEFAULTS.stream()
                .map(d -> new Category(event.userId(), d.name(), d.type(), d.color(), d.icon()))
                .toList();
        categoryRepository.saveAll(categories);
    }

    private record DefaultCategory(String name, CategoryType type, CategoryColor color, CategoryIcon icon) {
    }
}
