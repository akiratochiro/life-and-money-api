package com.akiratochiro.life_and_money_api.companyexpense;

import com.akiratochiro.life_and_money_api.attachment.Attachment;
import com.akiratochiro.life_and_money_api.attachment.AttachmentService;
import com.akiratochiro.life_and_money_api.attachment.FileType;
import com.akiratochiro.life_and_money_api.company.CompanyService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * As despesas da empresa (decisão 21). A avulsa é um fato: já foi paga na data informada. A fixa
 * repete o desenho das contas pessoais: a identidade, os termos com vigência e só o pagamento
 * gravado, como uma {@link CompanyExpense} com a fixa e o mês.
 */
@Service
public class CompanyExpenseService {

    static final Set<FileType> RECEIPT_TYPES = Set.of(FileType.PDF, FileType.JPEG, FileType.PNG);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final CompanyExpenseRepository expenseRepository;
    private final RecurringExpenseRepository recurringRepository;
    private final RecurringExpenseTermsRepository termsRepository;
    private final CompanyExpenseCategoryService categoryService;
    private final CompanyService companyService;
    private final AttachmentService attachmentService;
    private final Clock clock;

    public CompanyExpenseService(CompanyExpenseRepository expenseRepository,
                                 RecurringExpenseRepository recurringRepository,
                                 RecurringExpenseTermsRepository termsRepository,
                                 CompanyExpenseCategoryService categoryService,
                                 CompanyService companyService,
                                 AttachmentService attachmentService,
                                 Clock clock) {
        this.expenseRepository = expenseRepository;
        this.recurringRepository = recurringRepository;
        this.termsRepository = termsRepository;
        this.categoryService = categoryService;
        this.companyService = companyService;
        this.attachmentService = attachmentService;
        this.clock = clock;
    }

    // Avulsas

    @Transactional
    public CompanyExpense createOneOff(Long userId, Long companyId, OneOffExpenseRequest request) {
        companyService.getActiveOwned(userId, companyId);
        categoryService.getSelectable(companyId, request.categoryId());
        requireNotFuture(request.date());
        return expenseRepository.save(new CompanyExpense(userId, companyId, request.categoryId(), request.name().strip(),
                request.amount(), request.date(), null, null));
    }

    /**
     * Edita uma despesa paga. Numa avulsa, tudo muda; no pagamento de uma fixa, só o valor e a data:
     * o nome e a categoria vêm dos termos da fixa.
     */
    @Transactional
    public CompanyExpense update(Long userId, Long companyId, Long expenseId, OneOffExpenseRequest request) {
        companyService.getActiveOwned(userId, companyId);
        CompanyExpense expense = findExpense(companyId, expenseId);
        requireNotFuture(request.date());
        if (expense.isRecurringPayment()) {
            expense.change(expense.getCategoryId(), expense.getName(), request.amount(), request.date());
        } else {
            if (!request.categoryId().equals(expense.getCategoryId())) {
                categoryService.getSelectable(companyId, request.categoryId());
            }
            expense.change(request.categoryId(), request.name().strip(), request.amount(), request.date());
        }
        return expense;
    }

    /** Apaga a despesa e o comprovante. Se era o pagamento de uma fixa, o mês volta a pendente. */
    @Transactional
    public void delete(Long userId, Long companyId, Long expenseId) {
        companyService.getActiveOwned(userId, companyId);
        deleteWithReceipt(userId, findExpense(companyId, expenseId));
    }

    // Comprovantes

    /** Anexa ou troca o comprovante (PDF, JPEG ou PNG, pelas regras e pela cota dos anexos). */
    @Transactional
    public CompanyExpense attachReceipt(Long userId, Long companyId, Long expenseId, String filename, byte[] content) {
        companyService.getActiveOwned(userId, companyId);
        CompanyExpense expense = findExpense(companyId, expenseId);
        Long previous = expense.getAttachmentId();
        Attachment attachment = attachmentService.store(userId, filename, content, RECEIPT_TYPES);
        expense.attach(attachment.getId());
        if (previous != null) {
            expenseRepository.flush();
            attachmentService.delete(userId, previous);
        }
        return expense;
    }

    @Transactional(readOnly = true)
    public Attachment receipt(Long userId, Long companyId, Long expenseId) {
        companyService.getOwned(userId, companyId);
        CompanyExpense expense = findExpense(companyId, expenseId);
        if (expense.getAttachmentId() == null) {
            throw ExpenseNotFoundException.receipt();
        }
        return attachmentService.read(userId, expense.getAttachmentId());
    }

    @Transactional
    public void removeReceipt(Long userId, Long companyId, Long expenseId) {
        companyService.getActiveOwned(userId, companyId);
        CompanyExpense expense = findExpense(companyId, expenseId);
        Long attachmentId = expense.getAttachmentId();
        if (attachmentId != null) {
            expense.attach(null);
            expenseRepository.flush();
            attachmentService.delete(userId, attachmentId);
        }
    }

    // Fixas

    @Transactional
    public RecurringExpenseResponse createRecurring(Long userId, Long companyId, RecurringExpenseRequest request) {
        companyService.getActiveOwned(userId, companyId);
        categoryService.getSelectable(companyId, request.categoryId());
        if (request.startMonth() == null) {
            throw InvalidExpenseException.rule("Informe a partir de que mês a despesa vale.");
        }
        requireEndAfterStart(request.startMonth(), request.endMonth());

        RecurringExpense expense = recurringRepository.save(
                new RecurringExpense(userId, companyId, request.startMonth(), request.endMonth()));
        RecurringExpenseTerms terms = termsRepository.save(new RecurringExpenseTerms(userId, expense.getId(),
                request.categoryId(), request.name().strip(), request.amount(), request.dueDay(), request.startMonth()));
        return RecurringExpenseResponse.from(expense, terms, null);
    }

    @Transactional(readOnly = true)
    public List<RecurringExpenseResponse> listRecurring(Long userId, Long companyId) {
        companyService.getOwned(userId, companyId);
        List<RecurringExpense> expenses = recurringRepository.findAllByCompanyIdOrderByIdAsc(companyId);
        Map<Long, RecurringExpenseTerms> active = activeTerms(expenses);
        Collator collator = Collator.getInstance(Locale.of("pt", "BR"));
        collator.setStrength(Collator.SECONDARY);
        return expenses.stream()
                .map(expense -> RecurringExpenseResponse.from(expense, active.get(expense.getId()), null))
                .sorted(Comparator.comparing(RecurringExpenseResponse::name, collator::compare))
                .toList();
    }

    /**
     * Novos termos a partir do mês corrente, como nas contas pessoais: meses passados continuam com o
     * valor da época. Termos que começaram neste mês ainda não têm passado e são trocados no lugar.
     * O fim pode ser mudado ou removido, mas não para antes de um mês já pago.
     */
    @Transactional
    public RecurringExpenseResponse updateRecurring(Long userId, Long companyId, Long recurringId,
                                                    RecurringExpenseRequest request) {
        companyService.getActiveOwned(userId, companyId);
        RecurringExpense expense = findRecurring(companyId, recurringId);
        RecurringExpenseTerms current = termsRepository.findByRecurringExpenseIdAndValidToIsNull(recurringId).orElseThrow();
        if (!request.categoryId().equals(current.getCategoryId())) {
            categoryService.getSelectable(companyId, request.categoryId());
        }
        requireEndAfterStart(expense.getStartMonth(), request.endMonth());
        Optional<YearMonth> lastPaid = lastPaidMonth(recurringId);
        if (request.endMonth() != null && lastPaid.isPresent() && lastPaid.get().isAfter(request.endMonth())) {
            throw InvalidExpenseException.rule("O último mês não pode ser antes de " + MONTH.format(lastPaid.get())
                    + ", que já está pago.");
        }

        YearMonth thisMonth = YearMonth.now(clock);
        RecurringExpenseTerms terms;
        if (!current.getValidFrom().isBefore(thisMonth)) {
            current.replace(request.categoryId(), request.name().strip(), request.amount(), request.dueDay());
            terms = current;
        } else {
            current.close(thisMonth.minusMonths(1));
            // Fechar antes de inserir: o índice único parcial aceita só um termo aberto por fixa.
            termsRepository.flush();
            terms = termsRepository.save(new RecurringExpenseTerms(userId, recurringId, request.categoryId(),
                    request.name().strip(), request.amount(), request.dueDay(), thisMonth));
        }
        expense.endAt(request.endMonth());
        return RecurringExpenseResponse.from(expense, terms, null);
    }

    /**
     * Uma fixa sem pagamentos é só uma previsão, e é apagada. Com pagamentos, vira histórico: é
     * encerrada no mês anterior ao atual, ou no último mês pago, se for depois.
     */
    @Transactional
    public void deleteRecurring(Long userId, Long companyId, Long recurringId) {
        companyService.getActiveOwned(userId, companyId);
        endOrDelete(findRecurring(companyId, recurringId));
    }

    /** Pagar um mês: cria a despesa paga, com os termos do mês. Sem valor, o previsto; sem data, o vencimento. */
    @Transactional
    public CompanyExpense pay(Long userId, Long companyId, Long recurringId, YearMonth month,
                              PayRecurringExpenseRequest request) {
        companyService.getActiveOwned(userId, companyId);
        RecurringExpense expense = findRecurring(companyId, recurringId);
        ExpenseCalendar.Occurrence occurrence = occurrence(expense, month);
        if (expenseRepository.findByRecurringExpenseIdAndOccurrenceMonth(recurringId, month.atDay(1)).isPresent()) {
            throw InvalidExpenseException.alreadyPaid();
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate date = request != null && request.date() != null
                ? request.date()
                : (occurrence.dueDate().isAfter(today) ? today : occurrence.dueDate());
        requireNotFuture(date);
        BigDecimal amount = request != null && request.amount() != null ? request.amount() : occurrence.terms().getAmount();
        try {
            return expenseRepository.saveAndFlush(new CompanyExpense(userId, companyId, occurrence.terms().getCategoryId(),
                    occurrence.terms().getName(), amount, date, recurringId, month));
        } catch (DataIntegrityViolationException e) {
            throw InvalidExpenseException.alreadyPaid();
        }
    }

    /** Desfazer o pagamento apaga a despesa paga, e o mês volta a pendente (ou atrasado). */
    @Transactional
    public void unpay(Long userId, Long companyId, Long recurringId, YearMonth month) {
        companyService.getActiveOwned(userId, companyId);
        findRecurring(companyId, recurringId);
        CompanyExpense payment = expenseRepository.findByRecurringExpenseIdAndOccurrenceMonth(recurringId, month.atDay(1))
                .orElseThrow(() -> new ExpenseNotFoundException("Este mês não está pago."));
        deleteWithReceipt(userId, payment);
    }

    /** Arquivar a categoria encerra as fixas que estão nela, como nas contas pessoais. */
    @Transactional
    public void endRecurringOfCategory(Long categoryId) {
        for (RecurringExpenseTerms terms : termsRepository.findAllByCategoryIdAndValidToIsNull(categoryId)) {
            recurringRepository.findById(terms.getRecurringExpenseId()).ifPresent(this::endOrDelete);
        }
    }

    // O mês

    /**
     * O mês inteiro: as avulsas pela data, e as fixas pelo mês do vencimento (o aluguel de outubro
     * pago em 3 de novembro aparece em outubro). O total pago soma o que aparece pago; o pendente soma
     * o previsto das fixas ainda não pagas.
     */
    @Transactional(readOnly = true)
    public ExpenseMonthResponse month(Long userId, Long companyId, YearMonth month) {
        companyService.getOwned(userId, companyId);
        LocalDate today = LocalDate.now(clock);
        List<ExpenseMonthResponse.Item> items = new ArrayList<>();

        for (CompanyExpense oneOff : expenseRepository.findOneOffsBetween(companyId, month.atDay(1), month.atEndOfMonth())) {
            items.add(new ExpenseMonthResponse.Item(ExpenseMonthResponse.Kind.ONE_OFF, oneOff.getId(), null, oneOff.getName(),
                    oneOff.getCategoryId(), oneOff.getAmount(), null, oneOff.getExpenseDate(), null, ExpenseStatus.PAID,
                    oneOff.getAttachmentId() != null));
        }

        Map<Long, CompanyExpense> payments = expenseRepository.findAllByCompanyIdAndOccurrenceMonth(companyId, month.atDay(1))
                .stream().collect(Collectors.toMap(CompanyExpense::getRecurringExpenseId, Function.identity()));
        List<RecurringExpense> recurring = recurringRepository.findAllByCompanyIdOrderByIdAsc(companyId);
        for (ExpenseCalendar.Occurrence occurrence : ExpenseCalendar.in(month, recurring, termsByExpense(recurring))) {
            CompanyExpense payment = payments.get(occurrence.expense().getId());
            ExpenseStatus status = ExpenseCalendar.status(occurrence.dueDate(), payment != null, today);
            items.add(new ExpenseMonthResponse.Item(ExpenseMonthResponse.Kind.RECURRING,
                    payment == null ? null : payment.getId(), occurrence.expense().getId(),
                    payment == null ? occurrence.terms().getName() : payment.getName(),
                    payment == null ? occurrence.terms().getCategoryId() : payment.getCategoryId(),
                    payment == null ? occurrence.terms().getAmount() : payment.getAmount(),
                    occurrence.terms().getAmount(),
                    payment == null ? occurrence.dueDate() : payment.getExpenseDate(),
                    occurrence.dueDate(), status, payment != null && payment.getAttachmentId() != null));
        }

        Collator collator = Collator.getInstance(Locale.of("pt", "BR"));
        collator.setStrength(Collator.SECONDARY);
        items.sort(Comparator.comparing(ExpenseMonthResponse.Item::date)
                .thenComparing(ExpenseMonthResponse.Item::name, collator::compare));
        BigDecimal paid = sum(items, true);
        BigDecimal pending = sum(items, false);
        return new ExpenseMonthResponse(month, paid, pending, items);
    }

    // Conta

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listAllExpenses(Long userId) {
        return expenseRepository.findAllByUserIdOrderByCompanyIdAscExpenseDateAscIdAsc(userId).stream()
                .map(ExpenseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RecurringExpenseResponse> listAllRecurring(Long userId) {
        List<RecurringExpense> expenses = recurringRepository.findAllByUserIdOrderByIdAsc(userId);
        Map<Long, List<RecurringExpenseTerms>> history = termsByExpense(expenses);
        return expenses.stream().map(expense -> {
            List<RecurringExpenseTerms> terms = history.getOrDefault(expense.getId(), List.of());
            return RecurringExpenseResponse.from(expense, terms.getLast(), terms);
        }).toList();
    }

    /**
     * Para a exclusão da conta, na ordem das chaves: as despesas pagas (que apontam para as fixas,
     * as categorias e os anexos), os termos e as fixas. Os anexos e as categorias saem depois, pelos
     * serviços donos deles.
     */
    @Transactional
    public void deleteAllOf(Long userId) {
        expenseRepository.deleteAllByUserId(userId);
        termsRepository.deleteAllByUserId(userId);
        recurringRepository.deleteAllByUserId(userId);
    }

    private void endOrDelete(RecurringExpense expense) {
        Optional<YearMonth> lastPaid = lastPaidMonth(expense.getId());
        if (lastPaid.isEmpty()) {
            termsRepository.deleteAllByRecurringExpenseId(expense.getId());
            recurringRepository.deleteById(expense.getId());
            return;
        }
        YearMonth previous = YearMonth.now(clock).minusMonths(1);
        YearMonth lastMonth = lastPaid.get().isAfter(previous) ? lastPaid.get() : previous;
        if (expense.getEndMonth() == null || expense.getEndMonth().isAfter(lastMonth)) {
            expense.endAt(lastMonth);
        }
    }

    private ExpenseCalendar.Occurrence occurrence(RecurringExpense expense, YearMonth month) {
        return ExpenseCalendar.in(month, List.of(expense), termsByExpense(List.of(expense))).stream()
                .findFirst()
                .orElseThrow(ExpenseNotFoundException::occurrence);
    }

    private Map<Long, List<RecurringExpenseTerms>> termsByExpense(List<RecurringExpense> expenses) {
        if (expenses.isEmpty()) {
            return Map.of();
        }
        return termsRepository.findAllByRecurringExpenseIdInOrderByValidFromAsc(
                        expenses.stream().map(RecurringExpense::getId).toList()).stream()
                .collect(Collectors.groupingBy(RecurringExpenseTerms::getRecurringExpenseId));
    }

    private Map<Long, RecurringExpenseTerms> activeTerms(List<RecurringExpense> expenses) {
        return termsByExpense(expenses).values().stream()
                .flatMap(List::stream)
                .filter(RecurringExpenseTerms::isActive)
                .collect(Collectors.toMap(RecurringExpenseTerms::getRecurringExpenseId, Function.identity()));
    }

    private Optional<YearMonth> lastPaidMonth(Long recurringId) {
        return expenseRepository.findLastPaidMonth(recurringId).map(YearMonth::from);
    }

    private void deleteWithReceipt(Long userId, CompanyExpense expense) {
        Long attachmentId = expense.getAttachmentId();
        expenseRepository.delete(expense);
        if (attachmentId != null) {
            expenseRepository.flush();
            attachmentService.delete(userId, attachmentId);
        }
    }

    private CompanyExpense findExpense(Long companyId, Long expenseId) {
        return expenseRepository.findByIdAndCompanyId(expenseId, companyId).orElseThrow(ExpenseNotFoundException::expense);
    }

    private RecurringExpense findRecurring(Long companyId, Long recurringId) {
        return recurringRepository.findByIdAndCompanyId(recurringId, companyId).orElseThrow(ExpenseNotFoundException::recurring);
    }

    private void requireNotFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw InvalidExpenseException.rule("A data não pode estar no futuro: a despesa registra o que já foi pago.");
        }
    }

    private static void requireEndAfterStart(YearMonth start, YearMonth end) {
        if (end != null && end.isBefore(start)) {
            throw InvalidExpenseException.rule("O último mês não pode ser antes do primeiro.");
        }
    }

    private static BigDecimal sum(List<ExpenseMonthResponse.Item> items, boolean paid) {
        return items.stream()
                .filter(item -> (item.status() == ExpenseStatus.PAID) == paid)
                .map(ExpenseMonthResponse.Item::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
