package com.akiratochiro.life_and_money_api.bill;

import com.akiratochiro.life_and_money_api.category.Category;
import com.akiratochiro.life_and_money_api.category.CategoryService;
import com.akiratochiro.life_and_money_api.category.CategoryType;
import com.akiratochiro.life_and_money_api.transaction.Transaction;
import com.akiratochiro.life_and_money_api.transaction.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BillService {

    private final ScheduledBillRepository billRepository;
    private final BillTermsRepository termsRepository;
    private final BillPaymentRepository paymentRepository;
    private final CategoryService categoryService;
    private final TransactionService transactionService;
    private final Clock clock;

    public BillService(ScheduledBillRepository billRepository,
                       BillTermsRepository termsRepository,
                       BillPaymentRepository paymentRepository,
                       CategoryService categoryService,
                       TransactionService transactionService,
                       Clock clock) {
        this.billRepository = billRepository;
        this.termsRepository = termsRepository;
        this.paymentRepository = paymentRepository;
        this.categoryService = categoryService;
        this.transactionService = transactionService;
        this.clock = clock;
    }

    @Transactional
    public ScheduledBillResponse create(Long userId, CreateBillRequest request) {
        requireBillCategory(categoryService.getActiveOwned(userId, request.categoryId()));

        ScheduledBill bill = billRepository.save(new ScheduledBill(
                userId, request.recurrence(), request.startMonth(), request.endMonth()));
        BillTerms terms = termsRepository.save(new BillTerms(userId, bill.getId(), request.categoryId(),
                request.name(), request.amount(), request.dueDay(), bill.getStartMonth()));

        return ScheduledBillResponse.from(bill, terms);
    }

    @Transactional(readOnly = true)
    public List<ScheduledBillResponse> list(Long userId) {
        List<ScheduledBill> bills = billRepository.findAllByUserIdOrderByStartMonthAscIdAsc(userId);
        Map<Long, BillTerms> current = activeTermsOf(userId, bills);
        return bills.stream()
                .map(bill -> ScheduledBillResponse.from(bill, current.get(bill.getId())))
                .sorted(Comparator.comparing(ScheduledBillResponse::name, BillCalendar.byName()))
                .toList();
    }

    /** As ocorrências do mês, pagas ou não: é o que o calendário mostra. */
    @Transactional(readOnly = true)
    public List<BillOccurrenceResponse> occurrencesIn(Long userId, YearMonth month) {
        return occurrencesBetween(userId, month.atDay(1), month.atEndOfMonth());
    }

    /**
     * O que pede atenção agora: o que está atrasado (até {@value #OVERDUE_LOOKBACK_MONTHS} meses
     * para trás) e o que vence de hoje até daqui a {@value #UPCOMING_DAYS} dias. O que já foi pago
     * fica de fora.
     */
    @Transactional(readOnly = true)
    public List<BillOccurrenceResponse> upcoming(Long userId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = YearMonth.from(today).minusMonths(OVERDUE_LOOKBACK_MONTHS - 1).atDay(1);
        return occurrencesBetween(userId, from, today.plusDays(UPCOMING_DAYS)).stream()
                .filter(occurrence -> occurrence.status() != BillStatus.PAID)
                .toList();
    }

    static final int UPCOMING_DAYS = 7;
    static final int OVERDUE_LOOKBACK_MONTHS = 12;

    private List<BillOccurrenceResponse> occurrencesBetween(Long userId, LocalDate from, LocalDate to) {
        List<ScheduledBill> bills = billRepository.findAllActiveBetween(
                userId, YearMonth.from(from).atDay(1), YearMonth.from(to).atDay(1));
        if (bills.isEmpty()) {
            return List.of();
        }
        Map<Long, List<BillTerms>> terms = termsRepository
                .findAllByUserIdAndBillIdInOrderByValidFromAsc(userId, bills.stream().map(ScheduledBill::getId).toList())
                .stream().collect(Collectors.groupingBy(BillTerms::getBillId));
        Map<Long, CategoryType> types = categoryService.list(userId, true).stream()
                .collect(Collectors.toMap(Category::getId, Category::getType));

        List<Long> billIds = bills.stream().map(ScheduledBill::getId).toList();
        List<BillPayment> payments = paymentRepository.findAllInMonths(
                userId, billIds, YearMonth.from(from).atDay(1), YearMonth.from(to).atDay(1));
        Map<Long, Transaction> transactions = transactionService
                .findAllOwned(userId, payments.stream().map(BillPayment::getTransactionId).toList())
                .stream().collect(Collectors.toMap(Transaction::getId, Function.identity()));
        Map<String, Transaction> paidBy = payments.stream().collect(Collectors.toMap(
                payment -> key(payment.getBillId(), payment.getDueMonth()),
                payment -> transactions.get(payment.getTransactionId())));
        LocalDate today = LocalDate.now(clock);

        return BillCalendar.between(bills, terms, from, to).stream()
                .map(occurrence -> BillOccurrenceResponse.of(
                        occurrence,
                        types.get(occurrence.terms().getCategoryId()),
                        paidBy.get(key(occurrence.bill().getId(), occurrence.month())),
                        today))
                .toList();
    }

    private static String key(Long billId, YearMonth month) {
        return billId + "@" + month;
    }

    /**
     * Marca a ocorrência como paga criando a transação real, na categoria e com o nome da conta.
     * O valor e a data são os informados, ou o valor previsto e hoje.
     *
     * <p>A checagem de "já pago" dá uma mensagem clara; quem garante de fato é a restrição única
     * {@code (bill_id, due_month)}: dois cliques simultâneos passam pela checagem, mas o segundo
     * INSERT falha, e a transação que ele tinha criado é desfeita junto.
     */
    @Transactional
    public BillOccurrenceResponse pay(Long userId, Long billId, YearMonth month, PayBillRequest request) {
        ScheduledBill bill = findOwned(userId, billId);
        BillTerms terms = bill.occursIn(month)
                ? BillCalendar.termsIn(termsRepository.findAllByUserIdAndBillIdInOrderByValidFromAsc(userId, List.of(billId)), month)
                : null;
        if (terms == null) {
            throw new BillOccurrenceNotFoundException();
        }
        if (paymentRepository.existsByUserIdAndBillIdAndDueMonth(userId, billId, month.atDay(1))) {
            throw new BillAlreadyPaidException();
        }

        LocalDate today = LocalDate.now(clock);
        BigDecimal amount = request != null && request.amount() != null ? request.amount() : terms.getAmount();
        LocalDate paidOn = request != null && request.paidOn() != null ? request.paidOn() : today;

        Transaction transaction = transactionService.create(
                userId, terms.getCategoryId(), null, amount, paidOn, terms.getName());
        paymentRepository.save(new BillPayment(userId, billId, month, transaction.getId()));

        CategoryType type = categoryService.getOwned(userId, terms.getCategoryId()).getType();
        return BillOccurrenceResponse.of(
                new BillCalendar.Occurrence(bill, terms, month, terms.dueDateIn(month)), type, transaction, today);
    }

    /**
     * Desfaz o pagamento apagando a transação. Quem apaga o pagamento é o
     * {@link DeletedTransactionListener}, o mesmo caminho de excluir a transação pelo extrato:
     * assim só existe um jeito de um pagamento sumir.
     */
    @Transactional
    public void undoPayment(Long userId, Long billId, YearMonth month) {
        findOwned(userId, billId);
        BillPayment payment = paymentRepository.findByUserIdAndBillIdAndDueMonth(userId, billId, month.atDay(1))
                .orElseThrow(BillPaymentNotFoundException::new);
        transactionService.delete(userId, payment.getTransactionId());
    }

    /** Solta o pagamento ligado à transação, se houver: a ocorrência volta a não paga. */
    @Transactional
    public void releasePayment(Long userId, Long transactionId) {
        paymentRepository.deleteByTransactionId(userId, transactionId);
    }

    /**
     * Muda a conta a partir do mês atual, sem tocar nos meses passados: os termos em vigor são
     * fechados no mês anterior e outros são abertos, como no orçamento.
     *
     * <p>Quando não há mês passado a preservar, os termos são alterados no lugar: numa conta única,
     * que só tem um mês, e quando os termos em vigor começaram neste mês ou depois (uma conta
     * criada agora, ou que só começa no futuro).
     */
    @Transactional
    public ScheduledBillResponse update(Long userId, Long billId, UpdateBillRequest request) {
        ScheduledBill bill = findOwned(userId, billId);
        BillTerms current = activeTermsOf(bill);

        Category category = current.getCategoryId().equals(request.categoryId())
                ? categoryService.getOwned(userId, request.categoryId())
                : categoryService.getActiveOwned(userId, request.categoryId());
        requireBillCategory(category);

        changeEnd(bill, request.endMonth());

        if (sameTerms(current, request)) {
            return ScheduledBillResponse.from(bill, current);
        }

        YearMonth effectiveFrom = max(YearMonth.now(clock), bill.getStartMonth());
        if (bill.getRecurrence() == BillRecurrence.ONCE || !current.getValidFrom().isBefore(effectiveFrom)) {
            current.replace(request.categoryId(), request.name(), request.amount(), request.dueDay());
            return ScheduledBillResponse.from(bill, current);
        }
        if (bill.getEndMonth() != null && bill.getEndMonth().isBefore(effectiveFrom)) {
            throw new BillEndedException();
        }

        current.close(effectiveFrom.minusMonths(1));
        // O índice único parcial só permite um termo aberto por conta, e o Hibernate executa os
        // INSERTs antes dos UPDATEs: sem o flush, os dois termos ficariam abertos por um instante.
        termsRepository.flush();
        BillTerms next = termsRepository.save(new BillTerms(userId, bill.getId(), request.categoryId(),
                request.name(), request.amount(), request.dueDay(), effectiveFrom));
        return ScheduledBillResponse.from(bill, next);
    }

    /** Remoção pedida pelo usuário. */
    @Transactional
    public void remove(Long userId, Long billId) {
        endOrDelete(findOwned(userId, billId));
    }

    /**
     * Uma conta sem nenhum pagamento é só uma previsão, e é apagada de vez. Com pagamentos, ela
     * vira histórico: é encerrada no mês anterior ao atual, ou no último mês pago, se for depois.
     * Os meses passados continuam aparecendo, pagos ou atrasados, como estavam.
     */
    private void endOrDelete(ScheduledBill bill) {
        Optional<YearMonth> lastPaid = paymentRepository.findLastPaidMonth(bill.getId()).map(YearMonth::from);
        if (lastPaid.isEmpty()) {
            termsRepository.deleteAllByBillId(bill.getId());
            billRepository.deleteById(bill.getId());
            return;
        }

        YearMonth lastMonth = max(YearMonth.now(clock).minusMonths(1), lastPaid.get());
        if (bill.getEndMonth() == null || bill.getEndMonth().isAfter(lastMonth)) {
            bill.endAt(lastMonth);
        }
    }

    private void changeEnd(ScheduledBill bill, YearMonth endMonth) {
        if (bill.getRecurrence() == BillRecurrence.ONCE) {
            return;
        }
        if (endMonth != null) {
            if (endMonth.isBefore(bill.getStartMonth())) {
                throw new InvalidBillPeriodException();
            }
            Optional<LocalDate> lastPaid = paymentRepository.findLastPaidMonth(bill.getId());
            if (lastPaid.isPresent() && YearMonth.from(lastPaid.get()).isAfter(endMonth)) {
                throw new BillPaidAfterEndException();
            }
        }
        bill.endAt(endMonth);
    }

    /** Todo o histórico, para o export da conta. */
    @Transactional(readOnly = true)
    public List<BillExport> listAll(Long userId) {
        Map<Long, List<BillTerms>> terms = termsRepository.findAllByUserIdOrderByBillIdAscValidFromAsc(userId)
                .stream().collect(Collectors.groupingBy(BillTerms::getBillId));
        Map<Long, List<BillPayment>> payments = paymentRepository.findAllByUserIdOrderByBillIdAscDueMonthAsc(userId)
                .stream().collect(Collectors.groupingBy(BillPayment::getBillId));

        return billRepository.findAllByUserIdOrderByStartMonthAscIdAsc(userId).stream()
                .map(bill -> new BillExport(
                        bill.getId(),
                        bill.getRecurrence(),
                        bill.getStartMonth(),
                        bill.getEndMonth(),
                        terms.getOrDefault(bill.getId(), List.of()).stream().map(BillExport.Terms::from).toList(),
                        payments.getOrDefault(bill.getId(), List.of()).stream().map(BillExport.Payment::from).toList()))
                .toList();
    }

    /**
     * Para a exclusão da conta. Os pagamentos apontam para transações, então esta feature precisa
     * ser apagada antes das transações.
     */
    @Transactional
    public void deleteAllOf(Long userId) {
        paymentRepository.deleteAllByUserId(userId);
        termsRepository.deleteAllByUserId(userId);
        billRepository.deleteAllByUserId(userId);
    }

    private ScheduledBill findOwned(Long userId, Long billId) {
        return billRepository.findByIdAndUserId(billId, userId)
                .orElseThrow(BillNotFoundException::new);
    }

    /** Toda conta tem exatamente um termo aberto: fechar um termo sempre abre outro. */
    private BillTerms activeTermsOf(ScheduledBill bill) {
        return activeTermsOf(bill.getUserId(), List.of(bill)).get(bill.getId());
    }

    private Map<Long, BillTerms> activeTermsOf(Long userId, List<ScheduledBill> bills) {
        if (bills.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = bills.stream().map(ScheduledBill::getId).toList();
        return termsRepository.findAllByUserIdAndBillIdInOrderByValidFromAsc(userId, ids).stream()
                .filter(BillTerms::isActive)
                .collect(Collectors.toMap(BillTerms::getBillId, Function.identity()));
    }

    /** Poupança fica fora: um aporte agendado precisaria de meta, e isso é outra conversa. */
    private static void requireBillCategory(Category category) {
        if (category.getType() == CategoryType.SAVING) {
            throw new SavingCategoryBillException();
        }
    }

    private static boolean sameTerms(BillTerms terms, UpdateBillRequest request) {
        return terms.getCategoryId().equals(request.categoryId())
                && terms.getName().equals(request.name().strip())
                && terms.getAmount().compareTo(request.amount()) == 0
                && terms.getDueDay() == request.dueDay();
    }

    private static YearMonth max(YearMonth a, YearMonth b) {
        return a.isAfter(b) ? a : b;
    }
}
