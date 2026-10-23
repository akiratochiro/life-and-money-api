package com.akiratochiro.life_and_money_api.web;

import com.akiratochiro.life_and_money_api.budget.BudgetItemNotFoundException;
import com.akiratochiro.life_and_money_api.budget.IncomeCategoryBudgetException;
import com.akiratochiro.life_and_money_api.category.CategoryAlreadyExistsException;
import com.akiratochiro.life_and_money_api.category.CategoryArchivedException;
import com.akiratochiro.life_and_money_api.category.CategoryNotFoundException;
import com.akiratochiro.life_and_money_api.goal.InvalidGoalDeadlineException;
import com.akiratochiro.life_and_money_api.shared.GoalNotFoundException;
import com.akiratochiro.life_and_money_api.transaction.GoalRequiresSavingCategoryException;
import com.akiratochiro.life_and_money_api.transaction.TransactionNotFoundException;
import com.akiratochiro.life_and_money_api.user.EmailAlreadyInUseException;
import com.akiratochiro.life_and_money_api.user.InvalidCredentialsException;
import com.akiratochiro.life_and_money_api.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduz as exceções de regra de negócio em respostas RFC 9457 ({@link ProblemDetail}).
 *
 * <p>O campo {@code detail} é texto que o front mostra direto para o usuário, por isso está em
 * português e não carrega ids nem nomes de classe. Erros de validação ainda trazem a propriedade
 * {@code errors}, um mapa de campo para mensagem, que o front usa para marcar o campo no formulário.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 400

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Confira os campos destacados e tente de novo.");
        problem.setTitle("Dados inválidos");

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("errors", errors);

        return problem;
    }

    // 401

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Falha na autenticação");
        return problem;
    }

    // 404

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        return notFound("Usuário não encontrado", ex);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ProblemDetail handleCategoryNotFound(CategoryNotFoundException ex) {
        return notFound("Categoria não encontrada", ex);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ProblemDetail handleTransactionNotFound(TransactionNotFoundException ex) {
        return notFound("Transação não encontrada", ex);
    }

    @ExceptionHandler(GoalNotFoundException.class)
    public ProblemDetail handleGoalNotFound(GoalNotFoundException ex) {
        return notFound("Meta não encontrada", ex);
    }

    @ExceptionHandler(BudgetItemNotFoundException.class)
    public ProblemDetail handleBudgetItemNotFound(BudgetItemNotFoundException ex) {
        return notFound("Orçamento não encontrado", ex);
    }

    // 409

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ProblemDetail handleEmailAlreadyInUse(EmailAlreadyInUseException ex) {
        return conflict("E-mail já cadastrado", ex);
    }

    @ExceptionHandler(CategoryAlreadyExistsException.class)
    public ProblemDetail handleCategoryAlreadyExists(CategoryAlreadyExistsException ex) {
        return conflict("Categoria repetida", ex);
    }

    @ExceptionHandler(CategoryArchivedException.class)
    public ProblemDetail handleCategoryArchived(CategoryArchivedException ex) {
        return conflict("Categoria arquivada", ex);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        // A mensagem do banco não vai para o usuário, mas precisa ficar no log para investigação.
        log.warn("Violação de integridade no banco", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Esta operação conflita com dados que já existem.");
        problem.setTitle("Conflito de dados");
        return problem;
    }

    // 422

    @ExceptionHandler(IncomeCategoryBudgetException.class)
    public ProblemDetail handleIncomeCategoryBudget(IncomeCategoryBudgetException ex) {
        return unprocessable("Categoria inválida para orçamento", ex);
    }

    @ExceptionHandler(GoalRequiresSavingCategoryException.class)
    public ProblemDetail handleGoalRequiresSavingCategory(GoalRequiresSavingCategoryException ex) {
        return unprocessable("Categoria inválida para meta", ex);
    }

    @ExceptionHandler(InvalidGoalDeadlineException.class)
    public ProblemDetail handleInvalidGoalDeadline(InvalidGoalDeadlineException ex) {
        return unprocessable("Prazo inválido", ex);
    }

    private static ProblemDetail notFound(String title, RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, title, ex);
    }

    private static ProblemDetail conflict(String title, RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, title, ex);
    }

    private static ProblemDetail unprocessable(String title, RuntimeException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, title, ex);
    }

    private static ProblemDetail problem(HttpStatus status, String title, RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(title);
        return problem;
    }
}
