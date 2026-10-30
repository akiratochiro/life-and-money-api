package com.akiratochiro.life_and_money_api.transaction;

import com.akiratochiro.life_and_money_api.category.Category;
import com.akiratochiro.life_and_money_api.category.CategoryService;
import com.akiratochiro.life_and_money_api.category.CategoryType;
import com.akiratochiro.life_and_money_api.shared.GoalNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;
    private final GoalOwnershipChecker goalOwnershipChecker;

    public TransactionService(TransactionRepository transactionRepository, CategoryService categoryService, GoalOwnershipChecker goalOwnershipChecker) {
        this.transactionRepository = transactionRepository;
        this.categoryService = categoryService;
        this.goalOwnershipChecker = goalOwnershipChecker;
    }

    @Transactional
    public Transaction create(Long userId, Long categoryId, Long goalId, BigDecimal amount,
                              LocalDate transactionDate, String description) {
        Category category = categoryService.getActiveOwned(userId, categoryId);
        validateGoal(userId, goalId, category);
        return transactionRepository.save(
                new Transaction(userId, categoryId, goalId, amount, transactionDate, description));
    }

    @Transactional
    public Transaction update(Long userId, Long transactionId, Long categoryId, Long goalId,
                              BigDecimal amount, LocalDate transactionDate, String description) {
        Transaction transaction = findOwned(userId, transactionId);

        Category category = transaction.getCategoryId().equals(categoryId)
                ? categoryService.getOwned(userId, categoryId)
                : categoryService.getActiveOwned(userId, categoryId);

        validateGoal(userId, goalId, category);
        transaction.update(categoryId, goalId, amount, transactionDate, description);
        return transaction;
    }

    private void validateGoal(Long userId, Long goalId, Category category) {
        if (goalId == null) {
            return;
        }
        if (category.getType() != CategoryType.SAVING) {
            throw new GoalRequiresSavingCategoryException();
        }
        if (!goalOwnershipChecker.isOwnedBy(goalId, userId)) {
            throw new GoalNotFoundException();
        }
    }

    private Transaction findOwned(Long userId, Long transactionId) {
        return transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(TransactionNotFoundException::new);
    }


    @Transactional(readOnly = true)
    public List<Transaction> list(Long userId, YearMonth month){
        return transactionRepository.findAllByUserIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(userId, month.atDay(1), month.atEndOfMonth());
    }

    @Transactional
    public void delete(Long userId, Long transactionId){
        Transaction transaction = findOwned(userId, transactionId);
        transactionRepository.delete(transaction);
    }

    @Transactional(readOnly = true)
    public List<CategoryTotal> sumByCategory(Long userId, CategoryType type, YearMonth month) {
        return transactionRepository.sumByCategory(userId, type, month.atDay(1), month.atEndOfMonth());
    }

    @Transactional(readOnly = true)
    public List<CategoryTotal> sumExpensesByCategory(Long userId, YearMonth month) {
        return sumByCategory(userId, CategoryType.EXPENSE, month);
    }

    @Transactional(readOnly = true)
    public List<MonthlyTypeTotal> sumByMonthAndType(Long userId, YearMonth from, YearMonth to) {
        return transactionRepository.sumByMonthAndType(userId, from.atDay(1), to.atEndOfMonth());
    }



    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> savedByGoal(Long userId) {
        return transactionRepository.sumByGoal(userId).stream()
                .collect(Collectors.toMap(GoalTotal::goalId, GoalTotal::total));
    }

    /** Para a exclusão da conta: apaga tudo o que esta feature guarda do usuário. */
    @Transactional
    public void deleteAllOf(Long userId) {
        transactionRepository.deleteAllByUserId(userId);
    }
}
