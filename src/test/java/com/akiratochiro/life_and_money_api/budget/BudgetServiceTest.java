package com.akiratochiro.life_and_money_api.budget;

import com.akiratochiro.life_and_money_api.category.Category;
import com.akiratochiro.life_and_money_api.category.CategoryService;
import com.akiratochiro.life_and_money_api.category.CategoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CATEGORY_ID = 10L;

    @Mock
    private BudgetItemRepository budgetItemRepository;

    @Mock
    private CategoryService categoryService;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-15T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private BudgetService budgetService;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(budgetItemRepository, categoryService, clock);
    }

    @Test
    void replacingAnItemCreatedThisMonthDeletesIt() {
        // given
        Category rent = new Category(USER_ID, "Aluguel", CategoryType.EXPENSE);
        BudgetItem current = new BudgetItem(USER_ID, CATEGORY_ID, BudgetMode.AMOUNT,
                new BigDecimal("1500.00"), YearMonth.of(2026, 9));

        when(categoryService.getActiveOwned(USER_ID, CATEGORY_ID)).thenReturn(rent);
        when(budgetItemRepository.findByUserIdAndCategoryIdAndValidToIsNull(USER_ID, CATEGORY_ID))
                .thenReturn(Optional.of(current));
        when(budgetItemRepository.save(any(BudgetItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        BudgetItem created = budgetService.setBudget(USER_ID, CATEGORY_ID,
                BudgetMode.AMOUNT, new BigDecimal("1800.00"));

        // then
        verify(budgetItemRepository).delete(current);
        assertThat(current.isActive()).isTrue();
        assertThat(created.getLimitValue()).isEqualByComparingTo("1800.00");
        assertThat(created.getValidFrom()).isEqualTo(YearMonth.of(2026, 9));
    }

    @Test
    void replacingAnItemWithHistoryClosesItBeforeSavingTheNewOne() {
        // given
        Category rent = new Category(USER_ID, "Aluguel", CategoryType.EXPENSE);
        BudgetItem current = new BudgetItem(USER_ID, CATEGORY_ID, BudgetMode.AMOUNT,
                new BigDecimal("1500.00"), YearMonth.of(2026, 6));

        when(categoryService.getActiveOwned(USER_ID, CATEGORY_ID)).thenReturn(rent);
        when(budgetItemRepository.findByUserIdAndCategoryIdAndValidToIsNull(USER_ID, CATEGORY_ID))
                .thenReturn(Optional.of(current));
        when(budgetItemRepository.save(any(BudgetItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        budgetService.setBudget(USER_ID, CATEGORY_ID, BudgetMode.AMOUNT, new BigDecimal("1800.00"));

        // then
        assertThat(current.getValidTo()).isEqualTo(YearMonth.of(2026, 8));
        verify(budgetItemRepository, never()).delete(any());

        InOrder inOrder = inOrder(budgetItemRepository);
        inOrder.verify(budgetItemRepository).flush();
        inOrder.verify(budgetItemRepository).save(any(BudgetItem.class));
    }

    @Test
    void incomeCategoryCanNotHaveABudget(){
        // given
        Category salary = new Category(USER_ID, "Salario", CategoryType.INCOME);
        when(categoryService.getActiveOwned(USER_ID, CATEGORY_ID)).thenReturn(salary);

        // when / then
        assertThatThrownBy(() -> budgetService.setBudget(USER_ID, CATEGORY_ID,
                BudgetMode.AMOUNT, new BigDecimal("1000.00")))
                .isInstanceOf(IncomeCategoryBudgetException.class);

        verify(budgetItemRepository, never()).save(any());
    }

    @Test
    void endingTheBudgetOfACategoryWithoutOneDoesNothing() {
        // given
        when(budgetItemRepository.findByUserIdAndCategoryIdAndValidToIsNull(USER_ID, CATEGORY_ID))
                .thenReturn(Optional.empty());

        // when: é o que acontece ao arquivar uma categoria que nunca teve limite
        budgetService.endBudgetIfActive(USER_ID, CATEGORY_ID);

        // then: nada a encerrar, e nenhuma exceção
        verify(budgetItemRepository, never()).delete(any());
        verify(budgetItemRepository, never()).save(any());
        verify(budgetItemRepository, never()).flush();
    }

    @Test
    void endingABudgetWithHistoryClosesItAtTheEndOfLastMonth() {
        // given
        BudgetItem current = new BudgetItem(USER_ID, CATEGORY_ID, BudgetMode.AMOUNT,
                new BigDecimal("1500.00"), YearMonth.of(2026, 6));
        when(budgetItemRepository.findByUserIdAndCategoryIdAndValidToIsNull(USER_ID, CATEGORY_ID))
                .thenReturn(Optional.of(current));

        // when
        budgetService.endBudgetIfActive(USER_ID, CATEGORY_ID);

        // then: junho a agosto continuam valendo; de setembro em diante não há mais limite
        assertThat(current.getValidTo()).isEqualTo(YearMonth.of(2026, 8));
        verify(budgetItemRepository, never()).delete(any());
    }

    @Test
    void endingABudgetCreatedThisMonthDeletesItBecauseThereIsNoHistoryToKeep() {
        // given
        BudgetItem current = new BudgetItem(USER_ID, CATEGORY_ID, BudgetMode.AMOUNT,
                new BigDecimal("1500.00"), YearMonth.of(2026, 9));
        when(budgetItemRepository.findByUserIdAndCategoryIdAndValidToIsNull(USER_ID, CATEGORY_ID))
                .thenReturn(Optional.of(current));

        // when
        budgetService.endBudgetIfActive(USER_ID, CATEGORY_ID);

        // then: fechar em agosto seria um período inválido, então o item é apagado
        verify(budgetItemRepository).delete(current);
    }
}
