package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.company.Company;
import com.akiratochiro.life_and_money_api.company.CompanyRegime;
import com.akiratochiro.life_and_money_api.company.CompanyService;
import com.akiratochiro.life_and_money_api.companyexpense.CompanyExpense;
import com.akiratochiro.life_and_money_api.companyexpense.CompanyExpenseService;
import com.akiratochiro.life_and_money_api.holiday.HolidayCalendar;
import com.akiratochiro.life_and_money_api.holiday.HolidayService;
import com.akiratochiro.life_and_money_api.taxengine.ProLaboreAdvisor;
import com.akiratochiro.life_and_money_api.taxengine.TaxCalculationException;
import com.akiratochiro.life_and_money_api.taxengine.TaxTable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Os impostos da empresa: os vencimentos de cada mês (calculados, como as contas e as despesas
 * fixas), com o previsto e a memória de cálculo; os pagamentos, que viram despesa de impostos; as
 * obrigações personalizadas; e a calculadora de pró-labore.
 */
@Service
public class TaxService {

    static final String NOTICE = "Estimativa calculada pelo app: não substitui o seu contador.";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final TaxObligationRepository obligations;
    private final TaxPaymentRepository payments;
    private final CompanyService companyService;
    private final CompanyExpenseService expenseService;
    private final HolidayService holidayService;
    private final ExpectedTaxCalculator calculator;
    private final Clock clock;

    public TaxService(TaxObligationRepository obligations, TaxPaymentRepository payments, CompanyService companyService,
                      CompanyExpenseService expenseService, HolidayService holidayService, ExpectedTaxCalculator calculator,
                      Clock clock) {
        this.obligations = obligations;
        this.payments = payments;
        this.companyService = companyService;
        this.expenseService = expenseService;
        this.holidayService = holidayService;
        this.calculator = calculator;
        this.clock = clock;
    }

    // O mês

    /** Os vencimentos do mês: o DAS e o DARF da competência anterior, as declarações de março e maio, as personalizadas. */
    @Transactional(readOnly = true)
    public TaxMonthResponse month(Long userId, Long companyId, YearMonth month) {
        Company company = companyService.getOwned(userId, companyId);
        HolidayCalendar calendar = holidayService.calendarFor(month.getYear() - 1, month.getYear() + 1);
        List<TaxObligation> active = obligations.findAllByCompanyIdOrderByIdAsc(companyId).stream()
                .filter(TaxObligation::isActive).toList();
        Map<String, TaxPayment> paid = payments.findAllByObligationIdIn(active.stream().map(TaxObligation::getId).toList())
                .stream().collect(Collectors.toMap(p -> key(p.getObligationId(), p.getCompetenceMonth()), Function.identity()));

        List<Occurrence> occurrences = new ArrayList<>();
        for (TaxObligation obligation : active) {
            occurrenceDueIn(company, obligation, month, calendar).ifPresent(occurrences::add);
        }
        Map<Long, CompanyExpense> expenses = expenseService.expenses(companyId, occurrences.stream()
                .map(o -> paid.get(key(o.obligation().getId(), o.competence())))
                .filter(p -> p != null && p.getExpenseId() != null)
                .map(TaxPayment::getExpenseId).toList());

        List<TaxOccurrenceResponse> items = occurrences.stream()
                .map(o -> response(company, o, paid.get(key(o.obligation().getId(), o.competence())), expenses))
                .sorted(Comparator.comparing(TaxOccurrenceResponse::dueDate).thenComparing(TaxOccurrenceResponse::name))
                .toList();
        BigDecimal pending = items.stream().filter(i -> i.payment() == null && i.expectedAmount() != null)
                .map(TaxOccurrenceResponse::expectedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidTotal = items.stream().filter(i -> i.payment() != null && i.payment().paidAmount() != null)
                .map(i -> i.payment().paidAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TaxMonthResponse(month, pending, paidTotal, items, NOTICE);
    }

    // Pagamentos

    /**
     * Paga (ou, na declaração, marca como entregue) uma ocorrência. O valor pago vira uma despesa na
     * categoria Impostos, e o previsto do momento fica guardado para a diferença não mudar depois.
     */
    @Transactional
    public TaxOccurrenceResponse pay(Long userId, Long companyId, Long obligationId, YearMonth competence, PayTaxRequest request) {
        Company company = companyService.getActiveOwned(userId, companyId);
        TaxObligation obligation = findObligation(companyId, obligationId);
        Occurrence occurrence = occurrenceOf(company, obligation, competence)
                .orElseThrow(() -> new TaxNotFoundException("Esta obrigação não tem vencimento nessa competência."));
        if (payments.findByObligationIdAndCompetenceMonth(obligationId, competence.atDay(1)).isPresent()) {
            throw new InvalidTaxOperationException(obligation.getKind().isDeclaration()
                    ? "Esta declaração já está marcada como entregue." : "Este imposto já está pago.", true);
        }
        if (request.date().isAfter(LocalDate.now(clock))) {
            throw new InvalidTaxOperationException("A data não pode estar no futuro.", false);
        }
        ExpectedTax expected = expected(company, occurrence);
        TaxPayment payment;
        if (obligation.getKind().isDeclaration()) {
            payment = new TaxPayment(userId, obligationId, competence, null, null, request.date());
        } else {
            if (request.amount() == null) {
                throw new InvalidTaxOperationException("Informe o valor pago.", false);
            }
            CompanyExpense expense = expenseService.createTaxExpense(userId, companyId,
                    obligation.getName() + " · " + label(obligation, competence), request.amount(), request.date());
            payment = new TaxPayment(userId, obligationId, competence, expected.amount(), expense.getId(), null);
        }
        try {
            payment = payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            throw new InvalidTaxOperationException("Este imposto já está pago.", true);
        }
        Map<Long, CompanyExpense> expenses = payment.getExpenseId() == null ? Map.of()
                : expenseService.expenses(companyId, List.of(payment.getExpenseId()));
        return response(company, occurrence, payment, expenses);
    }

    /** Desfaz o pagamento (ou a entrega): a despesa de impostos some junto. */
    @Transactional
    public void unpay(Long userId, Long companyId, Long obligationId, YearMonth competence) {
        companyService.getActiveOwned(userId, companyId);
        findObligation(companyId, obligationId);
        TaxPayment payment = payments.findByObligationIdAndCompetenceMonth(obligationId, competence.atDay(1))
                .orElseThrow(() -> new TaxNotFoundException("Este vencimento não está pago."));
        Long expenseId = payment.getExpenseId();
        payments.delete(payment);
        payments.flush();
        if (expenseId != null) {
            expenseService.deleteTaxExpense(userId, companyId, expenseId);
        }
    }

    /** A despesa de um imposto pago foi apagada na aba Despesas: o vencimento volta a pendente. */
    @Transactional
    public void releaseExpense(Long expenseId) {
        payments.findByExpenseId(expenseId).ifPresent(payment -> {
            payments.delete(payment);
            payments.flush();
        });
    }

    // Obrigações

    @Transactional(readOnly = true)
    public List<ObligationResponse> obligations(Long userId, Long companyId) {
        companyService.getOwned(userId, companyId);
        return obligations.findAllByCompanyIdOrderByIdAsc(companyId).stream().map(ObligationResponse::from).toList();
    }

    @Transactional
    public ObligationResponse createCustom(Long userId, Long companyId, ObligationRequest request) {
        companyService.getActiveOwned(userId, companyId);
        validateCustom(request);
        return ObligationResponse.from(obligations.save(new TaxObligation(userId, companyId, ObligationKind.CUSTOM,
                request.name().strip(), request.recurrence(), request.dueDay(), dueMonth(request), request.dueRule(),
                request.fixedAmount(), request.startMonth(), endMonth(request))));
    }

    /** Ativar ou desativar qualquer obrigação; mudar os campos, só nas personalizadas. */
    @Transactional
    public ObligationResponse update(Long userId, Long companyId, Long obligationId, Boolean active, ObligationRequest changes) {
        companyService.getActiveOwned(userId, companyId);
        TaxObligation obligation = findObligation(companyId, obligationId);
        if (changes != null) {
            if (obligation.isSystem()) {
                throw new InvalidTaxOperationException("As obrigações do sistema só podem ser ativadas ou desativadas.", false);
            }
            validateCustom(changes);
            obligation.change(changes.name().strip(), changes.recurrence(), changes.dueDay(), dueMonth(changes),
                    changes.dueRule(), changes.fixedAmount(), changes.startMonth(), endMonth(changes));
        }
        if (active != null) {
            obligation.setActive(active);
        }
        return ObligationResponse.from(obligation);
    }

    @Transactional
    public void deleteCustom(Long userId, Long companyId, Long obligationId) {
        companyService.getActiveOwned(userId, companyId);
        TaxObligation obligation = findObligation(companyId, obligationId);
        if (obligation.isSystem()) {
            throw new InvalidTaxOperationException("As obrigações do sistema não podem ser excluídas, só desativadas.", false);
        }
        if (payments.existsByObligationId(obligationId)) {
            throw new InvalidTaxOperationException("Esta obrigação tem pagamentos registrados: desative em vez de excluir.", true);
        }
        obligations.delete(obligation);
    }

    // Calculadora

    /** Quanto de pró-labore leva o Fator R à meta da empresa, e quanto custa cada Anexo, com o faturamento informado. */
    @Transactional(readOnly = true)
    public ProLaboreCalculatorResponse proLaboreCalculator(Long userId, Long companyId, BigDecimal monthlyRevenue,
                                                           BigDecimal otherPayroll) {
        Company company = companyService.getOwned(userId, companyId);
        YearMonth now = YearMonth.now(clock);
        TaxTable table = calculator.table(now).orElseThrow(() -> new InvalidTaxOperationException(ExpectedTaxCalculator.NOT_CALCULATED, false));
        try {
            ProLaboreAdvisor.Result result = ProLaboreAdvisor.advise(monthlyRevenue, otherPayroll, company.getFatorRTarget(), table);
            return ProLaboreCalculatorResponse.from(result, NOTICE + " A comparação supõe o mesmo faturamento todo mês e "
                    + "nenhum dependente no IR.");
        } catch (TaxCalculationException e) {
            throw new InvalidTaxOperationException(e.getMessage(), false);
        }
    }

    // Conta

    @Transactional(readOnly = true)
    public List<ObligationResponse> listAllObligations(Long userId) {
        return obligations.findAllByUserIdOrderByCompanyIdAscIdAsc(userId).stream().map(ObligationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TaxPaymentExport> listAllPayments(Long userId) {
        return payments.findAllByUserIdOrderByObligationIdAscCompetenceMonthAsc(userId).stream().map(TaxPaymentExport::from).toList();
    }

    /** Para a exclusão da conta: os pagamentos (que apontam para as despesas) e as obrigações, antes de tudo. */
    @Transactional
    public void deleteAllOf(Long userId) {
        payments.deleteAllByUserId(userId);
        obligations.deleteAllByUserId(userId);
    }

    // Ocorrências

    record Occurrence(TaxObligation obligation, YearMonth competence, LocalDate dueDate, CompanyRegime regime) {
    }

    /** A ocorrência desta obrigação que vence no mês, se houver. */
    private Optional<Occurrence> occurrenceDueIn(Company company, TaxObligation obligation, YearMonth month, HolidayCalendar calendar) {
        YearMonth competence = switch (obligation.getRecurrence()) {
            case MONTHLY -> obligation.isSystem() ? month.minusMonths(1) : month;
            case YEARLY -> obligation.getDueMonth() == month.getMonthValue()
                    ? (obligation.isSystem() ? YearMonth.of(month.getYear() - 1, 1) : month) : null;
            case ONCE -> month;
        };
        if (competence == null) {
            return Optional.empty();
        }
        return occurrenceOf(company, obligation, competence, calendar);
    }

    private Optional<Occurrence> occurrenceOf(Company company, TaxObligation obligation, YearMonth competence) {
        YearMonth dueMonth = dueMonth(obligation, competence);
        return occurrenceOf(company, obligation, competence, holidayService.calendarFor(dueMonth.getYear() - 1, dueMonth.getYear() + 1));
    }

    /** A ocorrência da competência, se a obrigação vale nela (regime, período, pró-labore no DARF). */
    private Optional<Occurrence> occurrenceOf(Company company, TaxObligation obligation, YearMonth competence, HolidayCalendar calendar) {
        YearMonth opening = YearMonth.from(company.getOpeningDate());
        CompanyRegime regime = null;
        if (obligation.isSystem()) {
            if (obligation.getRecurrence() == Recurrence.YEARLY) {
                regime = regimeOfYear(company, obligation.getKind(), competence.getYear()).orElse(null);
            } else if (!competence.isBefore(opening)) {
                regime = companyService.regimeIn(company.getId(), competence)
                        .filter(r -> obligation.getKind().appliesTo(r.getTaxRegime())).orElse(null);
            }
            if (regime == null) {
                return Optional.empty();
            }
            if (obligation.getKind() == ObligationKind.DARF_PREVIDENCIARIO && !calculator.hasProLabore(company.getId(), competence)
                    && payments.findByObligationIdAndCompetenceMonth(obligation.getId(), competence.atDay(1)).isEmpty()) {
                return Optional.empty();
            }
        } else if (!withinCustomPeriod(obligation, competence)) {
            return Optional.empty();
        }
        return Optional.of(new Occurrence(obligation, competence, dueDate(obligation, competence, calendar), regime));
    }

    /** As declarações anuais valem nos anos em que algum mês teve o regime delas. */
    private Optional<CompanyRegime> regimeOfYear(Company company, ObligationKind kind, int year) {
        YearMonth opening = YearMonth.from(company.getOpeningDate());
        for (int m = 1; m <= 12; m++) {
            YearMonth month = YearMonth.of(year, m);
            if (month.isBefore(opening)) {
                continue;
            }
            Optional<CompanyRegime> regime = companyService.regimeIn(company.getId(), month)
                    .filter(r -> kind.appliesTo(r.getTaxRegime()));
            if (regime.isPresent()) {
                return regime;
            }
        }
        return Optional.empty();
    }

    private static boolean withinCustomPeriod(TaxObligation obligation, YearMonth competence) {
        YearMonth start = obligation.getStartMonth();
        YearMonth end = obligation.getEndMonth();
        boolean inRange = !competence.isBefore(start) && (end == null || !competence.isAfter(end));
        return switch (obligation.getRecurrence()) {
            case MONTHLY -> inRange;
            case YEARLY -> inRange && competence.getMonthValue() == obligation.getDueMonth();
            case ONCE -> competence.equals(start);
        };
    }

    private static YearMonth dueMonth(TaxObligation obligation, YearMonth competence) {
        if (!obligation.isSystem()) {
            return competence;
        }
        return obligation.getRecurrence() == Recurrence.YEARLY
                ? YearMonth.of(competence.getYear() + 1, obligation.getDueMonth())
                : competence.plusMonths(1);
    }

    private static LocalDate dueDate(TaxObligation obligation, YearMonth competence, HolidayCalendar calendar) {
        YearMonth month = dueMonth(obligation, competence);
        LocalDate date = month.atDay(Math.min(obligation.getDueDay(), month.lengthOfMonth()));
        return switch (obligation.getDueRule()) {
            case POSTPONE -> calendar.nextBusinessDayFrom(date);
            case ANTICIPATE -> calendar.previousBusinessDayFrom(date);
            case NONE -> date;
        };
    }

    private ExpectedTax expected(Company company, Occurrence occurrence) {
        TaxObligation obligation = occurrence.obligation();
        return switch (obligation.getKind()) {
            case DAS -> calculator.das(company, occurrence.regime(), occurrence.competence());
            case DAS_MEI -> calculator.dasMei(occurrence.regime(), occurrence.competence());
            case DARF_PREVIDENCIARIO -> calculator.darf(company, occurrence.competence());
            case DEFIS -> ExpectedTax.none(occurrence.competence().getYear() >= 2026
                    ? "Declaração anual, sem valor a pagar. A Resolução CGSN 190/2026 revoga a DEFIS a partir de 2027: "
                            + "confirme com o seu contador se a de " + occurrence.competence().getYear() + " ainda é entregue."
                    : "Declaração anual, sem valor a pagar.");
            case DASN_SIMEI -> ExpectedTax.none("Declaração anual, sem valor a pagar.");
            case CUSTOM -> obligation.getFixedAmount() != null
                    ? new ExpectedTax(obligation.getFixedAmount(), null, null)
                    : ExpectedTax.none("Sem valor fixo: informe o valor no pagamento.");
        };
    }

    private TaxOccurrenceResponse response(Company company, Occurrence occurrence, TaxPayment payment, Map<Long, CompanyExpense> expenses) {
        TaxObligation obligation = occurrence.obligation();
        ExpectedTax expected = expected(company, occurrence);
        TaxOccurrenceResponse.Payment paymentResponse = null;
        TaxOccurrenceResponse.Status status;
        if (payment != null) {
            CompanyExpense expense = payment.getExpenseId() == null ? null : expenses.get(payment.getExpenseId());
            BigDecimal paidAmount = expense == null ? null : expense.getAmount();
            BigDecimal difference = paidAmount != null && payment.getExpectedAmount() != null
                    ? paidAmount.subtract(payment.getExpectedAmount()) : null;
            paymentResponse = new TaxOccurrenceResponse.Payment(paidAmount, expense == null ? null : expense.getExpenseDate(),
                    payment.getDeliveredOn(), payment.getExpectedAmount(), difference, payment.getExpenseId());
            status = TaxOccurrenceResponse.Status.PAID;
        } else {
            status = occurrence.dueDate().isBefore(LocalDate.now(clock))
                    ? TaxOccurrenceResponse.Status.OVERDUE : TaxOccurrenceResponse.Status.PENDING;
        }
        return new TaxOccurrenceResponse(obligation.getId(), obligation.getKind(), obligation.getName(), occurrence.competence(),
                occurrence.dueDate(), status, expected.amount(), expected.note(), expected.calculation(), paymentResponse,
                obligation.getKind().isDeclaration());
    }

    private TaxObligation findObligation(Long companyId, Long obligationId) {
        return obligations.findByIdAndCompanyId(obligationId, companyId)
                .orElseThrow(() -> new TaxNotFoundException("Obrigação não encontrada."));
    }

    private static void validateCustom(ObligationRequest request) {
        if (request.recurrence() == Recurrence.YEARLY && request.dueMonth() == null) {
            throw new InvalidTaxOperationException("Informe o mês do vencimento da obrigação anual.", false);
        }
        if (request.endMonth() != null && request.endMonth().isBefore(request.startMonth())) {
            throw new InvalidTaxOperationException("O último mês não pode ser antes do primeiro.", false);
        }
    }

    private static Integer dueMonth(ObligationRequest request) {
        return request.recurrence() == Recurrence.YEARLY ? request.dueMonth() : null;
    }

    private static YearMonth endMonth(ObligationRequest request) {
        return request.recurrence() == Recurrence.ONCE ? request.startMonth() : request.endMonth();
    }

    private static String label(TaxObligation obligation, YearMonth competence) {
        return obligation.isSystem() && obligation.getRecurrence() == Recurrence.YEARLY
                ? String.valueOf(competence.getYear()) : MONTH.format(competence);
    }

    private static String key(Long obligationId, YearMonth competence) {
        return obligationId + ":" + competence;
    }
}
