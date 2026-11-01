package com.akiratochiro.life_and_money_api.bill;

import com.akiratochiro.life_and_money_api.category.Category;
import com.akiratochiro.life_and_money_api.category.CategoryService;
import com.akiratochiro.life_and_money_api.category.CategoryType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BillService {

    private final ScheduledBillRepository billRepository;
    private final BillTermsRepository termsRepository;
    private final CategoryService categoryService;
    private final Clock clock;

    public BillService(ScheduledBillRepository billRepository,
                       BillTermsRepository termsRepository,
                       CategoryService categoryService,
                       Clock clock) {
        this.billRepository = billRepository;
        this.termsRepository = termsRepository;
        this.categoryService = categoryService;
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
                .sorted(Comparator.comparing(ScheduledBillResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
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

    /** Enquanto não há pagamentos, uma conta é só uma previsão, e é apagada de vez. */
    private void endOrDelete(ScheduledBill bill) {
        termsRepository.deleteAllByBillId(bill.getId());
        billRepository.deleteById(bill.getId());
    }

    private void changeEnd(ScheduledBill bill, YearMonth endMonth) {
        if (bill.getRecurrence() == BillRecurrence.ONCE) {
            return;
        }
        if (endMonth != null && endMonth.isBefore(bill.getStartMonth())) {
            throw new InvalidBillPeriodException();
        }
        bill.endAt(endMonth);
    }

    /** Todo o histórico, para o export da conta. */
    @Transactional(readOnly = true)
    public List<BillExport> listAll(Long userId) {
        Map<Long, List<BillTerms>> terms = termsRepository.findAllByUserIdOrderByBillIdAscValidFromAsc(userId)
                .stream().collect(Collectors.groupingBy(BillTerms::getBillId));

        return billRepository.findAllByUserIdOrderByStartMonthAscIdAsc(userId).stream()
                .map(bill -> new BillExport(
                        bill.getId(),
                        bill.getRecurrence(),
                        bill.getStartMonth(),
                        bill.getEndMonth(),
                        terms.getOrDefault(bill.getId(), List.of()).stream().map(BillExport.Terms::from).toList()))
                .toList();
    }

    /** Para a exclusão da conta: apaga tudo o que esta feature guarda do usuário. */
    @Transactional
    public void deleteAllOf(Long userId) {
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
