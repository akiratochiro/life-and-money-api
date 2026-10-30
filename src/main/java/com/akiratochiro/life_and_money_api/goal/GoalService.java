package com.akiratochiro.life_and_money_api.goal;

import com.akiratochiro.life_and_money_api.shared.GoalNotFoundException;
import com.akiratochiro.life_and_money_api.transaction.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Service
public class GoalService {

    private final SavingsGoalRepository savingsGoalRepository;
    private final TransactionService transactionService;
    private final Clock clock;

    public GoalService(SavingsGoalRepository savingsGoalRepository,
                       TransactionService transactionService,
                       Clock clock) {
        this.savingsGoalRepository = savingsGoalRepository;
        this.transactionService = transactionService;
        this.clock = clock;
    }

    @Transactional
    public SavingsGoalResponse create(Long userId, String name, BigDecimal targetAmount, LocalDate deadline) {
        validateDeadline(deadline);
        SavingsGoal goal = savingsGoalRepository.save(new SavingsGoal(userId, name, targetAmount, deadline));
        return toResponse(goal, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<SavingsGoalResponse> list(Long userId) {
        Map<Long, BigDecimal> saved = transactionService.savedByGoal(userId);
        return savingsGoalRepository.findAllByUserIdOrderByDeadlineAsc(userId).stream()
                .map(goal -> toResponse(goal, saved.getOrDefault(goal.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional
    public SavingsGoalResponse update(Long userId, Long goalId, String name,
                                      BigDecimal targetAmount, LocalDate deadline) {
        SavingsGoal goal = findOwned(userId, goalId);
        validateDeadline(deadline);
        goal.update(name, targetAmount, deadline);
        return toResponse(goal, savedIn(userId, goalId));
    }

    @Transactional
    public GoalDeletionResponse delete(Long userId, Long goalId) {
        SavingsGoal goal = findOwned(userId, goalId);
        BigDecimal keptAsSavings = savedIn(userId, goalId);
        savingsGoalRepository.delete(goal);
        return new GoalDeletionResponse(goalId, keptAsSavings);
    }

    private SavingsGoalResponse toResponse(SavingsGoal goal, BigDecimal saved) {
        return SavingsGoalResponse.from(goal, goal.progress(saved, LocalDate.now(clock)));
    }

    private BigDecimal savedIn(Long userId, Long goalId) {
        return transactionService.savedByGoal(userId).getOrDefault(goalId, BigDecimal.ZERO);
    }

    private void validateDeadline(LocalDate deadline) {
        if (!YearMonth.from(deadline).isAfter(YearMonth.now(clock))) {
            throw new InvalidGoalDeadlineException();
        }
    }

    private SavingsGoal findOwned(Long userId, Long goalId) {
        return savingsGoalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(GoalNotFoundException::new);
    }

    /** Para a exclusão da conta: apaga tudo o que esta feature guarda do usuário. */
    @Transactional
    public void deleteAllOf(Long userId) {
        savingsGoalRepository.deleteAllByUserId(userId);
    }
}
