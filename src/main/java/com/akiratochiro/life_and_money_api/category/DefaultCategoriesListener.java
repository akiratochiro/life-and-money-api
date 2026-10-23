package com.akiratochiro.life_and_money_api.category;

import com.akiratochiro.life_and_money_api.user.UserRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class DefaultCategoriesListener {

    private static final List<DefaultCategory> DEFAULTS = List.of(
            new DefaultCategory("Salário", CategoryType.INCOME),
            new DefaultCategory("Mercado", CategoryType.EXPENSE),
            new DefaultCategory("Transporte", CategoryType.EXPENSE),
            new DefaultCategory("Moradia", CategoryType.EXPENSE),
            new DefaultCategory("Lazer", CategoryType.EXPENSE),
            new DefaultCategory("Investimentos", CategoryType.SAVING)
    );

    private final CategoryRepository categoryRepository;

    DefaultCategoriesListener(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @EventListener
    void onUserRegistered(UserRegisteredEvent event) {
        List<Category> categories = DEFAULTS.stream()
                .map(d -> new Category(event.userId(), d.name(), d.type()))
                .toList();
        categoryRepository.saveAll(categories);
    }

    private record DefaultCategory(String name, CategoryType type) {
    }
}
