package com.akiratochiro.life_and_money_api.budget;


import com.akiratochiro.life_and_money_api.category.Category;
import com.akiratochiro.life_and_money_api.category.CategoryService;
import com.akiratochiro.life_and_money_api.category.CategoryType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

@Service
public class BudgetService {

    private final BudgetItemRepository budgetItemRepository;
    private final CategoryService categoryService;
    private final Clock clock;

    public BudgetService(BudgetItemRepository budgetItemRepository,
                         CategoryService categoryService,
                         Clock clock) {
        this.budgetItemRepository = budgetItemRepository;
        this.categoryService = categoryService;
        this.clock = clock;
    }

    @Transactional
    public BudgetItem setBudget(Long userId, Long categoryId, BudgetMode mode, BigDecimal limitValue) {
        Category category = categoryService.getActiveOwned(userId, categoryId);
        if (category.getType() == CategoryType.INCOME) {
            throw new IncomeCategoryBudgetException();
        }

        YearMonth currentMonth = YearMonth.now(clock);

        budgetItemRepository.findByUserIdAndCategoryIdAndValidToIsNull(userId, categoryId)
                .ifPresent(current -> endVigency(current, currentMonth));

        return budgetItemRepository.save(
                new BudgetItem(userId, categoryId, mode, limitValue, currentMonth));
    }

    /** Remoção pedida pelo usuário: se não havia orçamento, o pedido não faz sentido e falha. */
    @Transactional
    public void removeFromBudget(Long userId, Long categoryId) {
        BudgetItem current = budgetItemRepository
                .findByUserIdAndCategoryIdAndValidToIsNull(userId, categoryId)
                .orElseThrow(BudgetItemNotFoundException::new);

        endVigency(current, YearMonth.now(clock));
    }

    /**
     * Encerra o orçamento em vigor, se existir. Diferente do {@link #removeFromBudget}, aqui a
     * ausência de orçamento é normal: quem chama reage a um fato (a categoria foi arquivada),
     * não a um pedido de remoção.
     */
    @Transactional
    public void endBudgetIfActive(Long userId, Long categoryId) {
        budgetItemRepository.findByUserIdAndCategoryIdAndValidToIsNull(userId, categoryId)
                .ifPresent(current -> endVigency(current, YearMonth.now(clock)));
    }

    private void endVigency(BudgetItem item, YearMonth currentMonth) {
        if (item.getValidFrom().equals(currentMonth)) {
            budgetItemRepository.delete(item);
        } else {
            item.close(currentMonth.minusMonths(1));
        }
        budgetItemRepository.flush();
    }

    @Transactional(readOnly = true)
    public List<BudgetItem> listForMonth(Long userId, YearMonth month) {
        return budgetItemRepository.findAllValidInMonth(userId, month.atDay(1));
    }

    /** Para a exclusão da conta: apaga tudo o que esta feature guarda do usuário. */
    @Transactional
    public void deleteAllOf(Long userId) {
        budgetItemRepository.deleteAllByUserId(userId);
    }
}
