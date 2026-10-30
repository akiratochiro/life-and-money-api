package com.akiratochiro.life_and_money_api.account;

import com.akiratochiro.life_and_money_api.auth.AccountCredentials;
import com.akiratochiro.life_and_money_api.budget.BudgetItemResponse;
import com.akiratochiro.life_and_money_api.budget.BudgetService;
import com.akiratochiro.life_and_money_api.category.CategoryResponse;
import com.akiratochiro.life_and_money_api.category.CategoryService;
import com.akiratochiro.life_and_money_api.goal.GoalService;
import com.akiratochiro.life_and_money_api.ratelimit.RateLimit;
import com.akiratochiro.life_and_money_api.ratelimit.RateLimiter;
import com.akiratochiro.life_and_money_api.transaction.TransactionResponse;
import com.akiratochiro.life_and_money_api.transaction.TransactionService;
import com.akiratochiro.life_and_money_api.user.UserResponse;
import com.akiratochiro.life_and_money_api.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * A conta como um todo: excluir e exportar tudo o que é do usuário.
 *
 * <p>Fica num pacote próprio porque precisa de todas as features, e nenhuma delas precisa dele —
 * como o {@code dashboard}. Se a exclusão morasse em {@code user}, ele passaria a depender de
 * {@code category}, que já depende dele: um ciclo.
 */
@Service
class AccountService {

    private final UserService userService;
    private final CategoryService categoryService;
    private final TransactionService transactionService;
    private final BudgetService budgetService;
    private final GoalService goalService;
    private final AccountCredentials accountCredentials;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    AccountService(UserService userService,
                   CategoryService categoryService,
                   TransactionService transactionService,
                   BudgetService budgetService,
                   GoalService goalService,
                   AccountCredentials accountCredentials,
                   RateLimiter rateLimiter,
                   Clock clock) {
        this.userService = userService;
        this.categoryService = categoryService;
        this.transactionService = transactionService;
        this.budgetService = budgetService;
        this.goalService = goalService;
        this.accountCredentials = accountCredentials;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** Uma leitura só, numa transação somente leitura: o export é uma foto consistente da conta. */
    @Transactional(readOnly = true)
    AccountExport export(Long userId) {
        return new AccountExport(
                clock.instant(),
                UserResponse.from(userService.findById(userId)),
                categoryService.list(userId, true).stream().map(CategoryResponse::from).toList(),
                transactionService.listAll(userId).stream().map(TransactionResponse::from).toList(),
                budgetService.listAll(userId).stream().map(BudgetItemResponse::from).toList(),
                goalService.list(userId));
    }

    /**
     * Apaga a conta e todos os dados dela, numa transação só: ou tudo some, ou nada muda.
     *
     * <p>A ordem segue as chaves estrangeiras, de quem aponta para quem é apontado: transações
     * apontam para categorias e metas, o orçamento aponta para categorias, e tudo aponta para o
     * usuário. As chaves não têm cascata de propósito: se uma feature nova esquecer de entrar
     * aqui, a exclusão do usuário falha com erro de integridade, em vez de deixar dados para trás.
     *
     * @throws com.akiratochiro.life_and_money_api.user.IncorrectPasswordException se a senha não confere
     */
    @Transactional
    void deleteAccount(Long userId, String password) {
        rateLimiter.consume(RateLimit.ACCOUNT_DELETION_PER_USER, String.valueOf(userId));
        userService.verifyPassword(userId, password);

        transactionService.deleteAllOf(userId);
        budgetService.deleteAllOf(userId);
        goalService.deleteAllOf(userId);
        categoryService.deleteAllOf(userId);
        accountCredentials.deleteAllOf(userId);
        userService.delete(userId);
    }
}
