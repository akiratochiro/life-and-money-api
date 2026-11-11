package com.akiratochiro.life_and_money_api.web;

import com.akiratochiro.life_and_money_api.attachment.InvalidAttachmentException;
import com.akiratochiro.life_and_money_api.auth.BreachedPasswordException;
import com.akiratochiro.life_and_money_api.auth.InvalidConfirmationTokenException;
import com.akiratochiro.life_and_money_api.auth.InvalidPasswordResetTokenException;
import com.akiratochiro.life_and_money_api.auth.InvalidRefreshTokenException;
import com.akiratochiro.life_and_money_api.bill.BillAlreadyPaidException;
import com.akiratochiro.life_and_money_api.bill.BillEndedException;
import com.akiratochiro.life_and_money_api.bill.BillNotFoundException;
import com.akiratochiro.life_and_money_api.bill.BillOccurrenceNotFoundException;
import com.akiratochiro.life_and_money_api.bill.BillPaidAfterEndException;
import com.akiratochiro.life_and_money_api.bill.BillPaymentNotFoundException;
import com.akiratochiro.life_and_money_api.bill.InvalidBillPeriodException;
import com.akiratochiro.life_and_money_api.bill.SavingCategoryBillException;
import com.akiratochiro.life_and_money_api.budget.BudgetItemNotFoundException;
import com.akiratochiro.life_and_money_api.budget.IncomeCategoryBudgetException;
import com.akiratochiro.life_and_money_api.category.CategoryAlreadyExistsException;
import com.akiratochiro.life_and_money_api.category.CategoryArchivedException;
import com.akiratochiro.life_and_money_api.category.CategoryNotFoundException;
import com.akiratochiro.life_and_money_api.company.CnpjAlreadyRegisteredException;
import com.akiratochiro.life_and_money_api.company.CompanyArchivedException;
import com.akiratochiro.life_and_money_api.company.CompanyNotFoundException;
import com.akiratochiro.life_and_money_api.company.InvalidCompanyDataException;
import com.akiratochiro.life_and_money_api.company.InvalidPartnerChangeException;
import com.akiratochiro.life_and_money_api.company.PartnerNotFoundException;
import com.akiratochiro.life_and_money_api.company.InvalidRegimeChangeException;
import com.akiratochiro.life_and_money_api.company.NoRegimeChangeToUndoException;
import com.akiratochiro.life_and_money_api.company.OpeningDateLockedException;
import com.akiratochiro.life_and_money_api.goal.InvalidGoalDeadlineException;
import com.akiratochiro.life_and_money_api.invoice.DuplicateInvoiceException;
import com.akiratochiro.life_and_money_api.invoice.InvalidInvoiceException;
import com.akiratochiro.life_and_money_api.invoice.InvalidRevenueRangeException;
import com.akiratochiro.life_and_money_api.invoice.InvoiceNotFoundException;
import com.akiratochiro.life_and_money_api.invoice.InvoiceParseException;
import com.akiratochiro.life_and_money_api.ratelimit.RateLimitExceededException;
import com.akiratochiro.life_and_money_api.shared.GoalNotFoundException;
import com.akiratochiro.life_and_money_api.transaction.GoalRequiresSavingCategoryException;
import com.akiratochiro.life_and_money_api.transaction.TransactionNotFoundException;
import com.akiratochiro.life_and_money_api.user.EmailAlreadyInUseException;
import com.akiratochiro.life_and_money_api.user.IncorrectPasswordException;
import com.akiratochiro.life_and_money_api.user.InvalidCredentialsException;
import com.akiratochiro.life_and_money_api.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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

    /**
     * Um corpo que o Jackson não consegue ler. O caso que importa é um valor fora de uma lista
     * fechada (cor ou ícone de categoria, tipo): o campo vai marcado, como num erro de validação.
     * Sem isso, a resposta seria a padrão do Spring, em inglês e sem dizer qual campo.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Não foi possível ler os dados enviados.");
        problem.setTitle("Dados inválidos");

        if (ex.getCause() instanceof InvalidFormatException invalid) {
            String field = invalid.getPath().stream()
                    .map(JacksonException.Reference::getPropertyName)
                    .filter(Objects::nonNull)
                    .reduce((first, last) -> last)
                    .orElse(null);
            if (field != null) {
                boolean closedList = invalid.getTargetType() != null && invalid.getTargetType().isEnum();
                problem.setDetail("Confira os campos destacados e tente de novo.");
                problem.setProperty("errors", Map.of(field,
                        closedList ? "Escolha uma das opções disponíveis." : "Valor inválido."));
            }
        }
        return problem;
    }

    /** Como um erro de validação: o campo da senha vai marcado no formulário. */
    @ExceptionHandler(BreachedPasswordException.class)
    public ProblemDetail handleBreachedPassword(BreachedPasswordException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Confira os campos destacados e tente de novo.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", Map.of(ex.getField(), ex.getMessage()));
        return problem;
    }

    /** Uma regra da empresa (enquadramento, CNPJ, emoji, datas): o campo vai marcado no formulário. */
    @ExceptionHandler(InvalidCompanyDataException.class)
    public ProblemDetail handleInvalidCompanyData(InvalidCompanyDataException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Confira os campos destacados e tente de novo.");
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", Map.of(ex.getField(), ex.getMessage()));
        return problem;
    }

    /** Um envio sem a parte do arquivo: o formulário mandou o pedido sem anexar nada. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail handleMissingPart(MissingServletRequestPartException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Escolha um arquivo para enviar.");
        problem.setTitle("Arquivo ausente");
        return problem;
    }

    @ExceptionHandler(InvalidRevenueRangeException.class)
    public ProblemDetail handleInvalidRevenueRange(InvalidRevenueRangeException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Período inválido", ex);
    }

    @ExceptionHandler(InvalidConfirmationTokenException.class)
    public ProblemDetail handleInvalidConfirmationToken(InvalidConfirmationTokenException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Link inválido", ex);
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    public ProblemDetail handleInvalidPasswordResetToken(InvalidPasswordResetTokenException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Link inválido", ex);
    }

    // 401

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Falha na autenticação");
        return problem;
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Sessão encerrada");
        return problem;
    }

    // 403

    /** Com o campo marcado, para o front mostrar o erro no campo de senha. */
    @ExceptionHandler(IncorrectPasswordException.class)
    public ProblemDetail handleIncorrectPassword(IncorrectPasswordException ex) {
        ProblemDetail problem = problem(HttpStatus.FORBIDDEN, "Senha incorreta", ex);
        problem.setProperty("errors", Map.of("password", ex.getMessage()));
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

    @ExceptionHandler(BillNotFoundException.class)
    public ProblemDetail handleBillNotFound(BillNotFoundException ex) {
        return notFound("Conta não encontrada", ex);
    }

    @ExceptionHandler(BillOccurrenceNotFoundException.class)
    public ProblemDetail handleBillOccurrenceNotFound(BillOccurrenceNotFoundException ex) {
        return notFound("Vencimento não encontrado", ex);
    }

    @ExceptionHandler(BillPaymentNotFoundException.class)
    public ProblemDetail handleBillPaymentNotFound(BillPaymentNotFoundException ex) {
        return notFound("Pagamento não encontrado", ex);
    }

    @ExceptionHandler(InvoiceNotFoundException.class)
    public ProblemDetail handleInvoiceNotFound(InvoiceNotFoundException ex) {
        return notFound("Nota fiscal não encontrada", ex);
    }

    @ExceptionHandler(CompanyNotFoundException.class)
    public ProblemDetail handleCompanyNotFound(CompanyNotFoundException ex) {
        return notFound("Empresa não encontrada", ex);
    }

    @ExceptionHandler(PartnerNotFoundException.class)
    public ProblemDetail handlePartnerNotFound(PartnerNotFoundException ex) {
        return notFound("Sócio não encontrado", ex);
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

    @ExceptionHandler(BillAlreadyPaidException.class)
    public ProblemDetail handleBillAlreadyPaid(BillAlreadyPaidException ex) {
        return conflict("Mês já pago", ex);
    }

    @ExceptionHandler(BillPaidAfterEndException.class)
    public ProblemDetail handleBillPaidAfterEnd(BillPaidAfterEndException ex) {
        return conflict("Mês pago depois do fim", ex);
    }

    @ExceptionHandler(BillEndedException.class)
    public ProblemDetail handleBillEnded(BillEndedException ex) {
        return conflict("Conta encerrada", ex);
    }

    /** Com o campo marcado, como um erro de validação, mas 409: o valor é válido, só já existe. */
    @ExceptionHandler(CnpjAlreadyRegisteredException.class)
    public ProblemDetail handleCnpjAlreadyRegistered(CnpjAlreadyRegisteredException ex) {
        ProblemDetail problem = conflict("CNPJ repetido", ex);
        problem.setProperty("errors", Map.of("cnpj", ex.getMessage()));
        return problem;
    }

    @ExceptionHandler(CompanyArchivedException.class)
    public ProblemDetail handleCompanyArchived(CompanyArchivedException ex) {
        return conflict("Empresa arquivada", ex);
    }

    @ExceptionHandler(OpeningDateLockedException.class)
    public ProblemDetail handleOpeningDateLocked(OpeningDateLockedException ex) {
        ProblemDetail problem = conflict("Data de abertura travada", ex);
        problem.setProperty("errors", Map.of("openingDate", ex.getMessage()));
        return problem;
    }

    @ExceptionHandler(NoRegimeChangeToUndoException.class)
    public ProblemDetail handleNoRegimeChangeToUndo(NoRegimeChangeToUndoException ex) {
        return conflict("Nada para desfazer", ex);
    }

    @ExceptionHandler(DuplicateInvoiceException.class)
    public ProblemDetail handleDuplicateInvoice(DuplicateInvoiceException ex) {
        return conflict("Nota repetida", ex);
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

    @ExceptionHandler(SavingCategoryBillException.class)
    public ProblemDetail handleSavingCategoryBill(SavingCategoryBillException ex) {
        return unprocessable("Categoria inválida para conta", ex);
    }

    @ExceptionHandler(InvalidBillPeriodException.class)
    public ProblemDetail handleInvalidBillPeriod(InvalidBillPeriodException ex) {
        return unprocessable("Período inválido", ex);
    }

    @ExceptionHandler(InvalidRegimeChangeException.class)
    public ProblemDetail handleInvalidRegimeChange(InvalidRegimeChangeException ex) {
        return unprocessable("Mudança de enquadramento inválida", ex);
    }

    @ExceptionHandler(InvalidPartnerChangeException.class)
    public ProblemDetail handleInvalidPartnerChange(InvalidPartnerChangeException ex) {
        return unprocessable("Mudança de sócios inválida", ex);
    }

    @ExceptionHandler(InvalidAttachmentException.class)
    public ProblemDetail handleInvalidAttachment(InvalidAttachmentException ex) {
        return unprocessable("Arquivo recusado", ex);
    }

    @ExceptionHandler(InvoiceParseException.class)
    public ProblemDetail handleInvoiceParse(InvoiceParseException ex) {
        return unprocessable("Nota não reconhecida", ex);
    }

    @ExceptionHandler(InvalidInvoiceException.class)
    public ProblemDetail handleInvalidInvoice(InvalidInvoiceException ex) {
        return unprocessable("Nota recusada", ex);
    }

    // 413

    /** O Spring recusa o envio antes de chegar ao controller, pelo limite de multipart da configuração. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
                "Cada arquivo pode ter no máximo 2 MB, e cada envio, até 10 arquivos.");
        problem.setTitle("Arquivo grande demais");
        return problem;
    }

    // 429

    /**
     * O tempo de espera vai em dois lugares: no cabeçalho {@code Retry-After}, que é o padrão HTTP
     * e o que clientes genéricos entendem, e em {@code retryAfterSeconds} no corpo, que o front lê
     * junto com o resto do ProblemDetail.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException ex) {
        long seconds = Math.max(1, (ex.getRetryAfter().toMillis() + 999) / 1000);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas em pouco tempo. Tente de novo em " + humanize(seconds) + ".");
        problem.setTitle("Muitas tentativas");
        problem.setProperty("retryAfterSeconds", seconds);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(problem);
    }

    private static String humanize(long seconds) {
        if (seconds < 60) {
            return seconds == 1 ? "1 segundo" : seconds + " segundos";
        }
        long minutes = (seconds + 59) / 60;
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
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
