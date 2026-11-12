package com.akiratochiro.life_and_money_api.withdrawal;

import com.akiratochiro.life_and_money_api.company.Company;
import com.akiratochiro.life_and_money_api.company.CompanyRegime;
import com.akiratochiro.life_and_money_api.company.CompanyService;
import com.akiratochiro.life_and_money_api.company.LegalNature;
import com.akiratochiro.life_and_money_api.company.Partner;
import com.akiratochiro.life_and_money_api.company.PartnerService;
import com.akiratochiro.life_and_money_api.company.TaxRegime;
import com.akiratochiro.life_and_money_api.taxengine.Money;
import com.akiratochiro.life_and_money_api.taxengine.ProLaboreCalculator;
import com.akiratochiro.life_and_money_api.taxengine.ProfitDistribution;
import com.akiratochiro.life_and_money_api.taxengine.TaxParameter;
import com.akiratochiro.life_and_money_api.taxengine.TaxRules;
import com.akiratochiro.life_and_money_api.taxengine.TaxTable;
import com.akiratochiro.life_and_money_api.category.Category;
import com.akiratochiro.life_and_money_api.category.CategoryColor;
import com.akiratochiro.life_and_money_api.category.CategoryIcon;
import com.akiratochiro.life_and_money_api.category.CategoryService;
import com.akiratochiro.life_and_money_api.category.CategoryType;
import com.akiratochiro.life_and_money_api.taxparam.TaxParameterService;
import com.akiratochiro.life_and_money_api.transaction.Transaction;
import com.akiratochiro.life_and_money_api.transaction.TransactionOrigin;
import com.akiratochiro.life_and_money_api.transaction.TransactionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Pró-labore (Fase 4) e distribuição de lucros (Fase 6). O pró-labore tem o INSS e o IRRF calculados
 * pelo motor tributário nos meses que o app calcula; nos outros (antes de 2026), eles são informados
 * como estão no holerite, para a folha do Fator R ter o histórico. A distribuição não tem INSS, tem o
 * IRRF que o usuário informar, e volta com avisos (retenção acima de R$ 50 mil, limite de isenção) que
 * nunca bloqueiam (decisão 28).
 */
@Service
public class WithdrawalService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final WithdrawalRepository withdrawals;
    private final ProLaborePlanRepository plans;
    private final CompanyService companyService;
    private final PartnerService partnerService;
    private final TaxParameterService taxParameters;
    /** Pelo provider: o implementador (em {@code tax}) depende deste service, e o construtor faria um ciclo de beans. */
    private final ObjectProvider<ProfitExemptionLimits> exemptionLimits;
    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final Clock clock;

    public WithdrawalService(WithdrawalRepository withdrawals, ProLaborePlanRepository plans, CompanyService companyService,
                             PartnerService partnerService, TaxParameterService taxParameters,
                             ObjectProvider<ProfitExemptionLimits> exemptionLimits, TransactionService transactionService,
                             CategoryService categoryService, Clock clock) {
        this.transactionService = transactionService;
        this.categoryService = categoryService;
        this.withdrawals = withdrawals;
        this.plans = plans;
        this.companyService = companyService;
        this.partnerService = partnerService;
        this.taxParameters = taxParameters;
        this.exemptionLimits = exemptionLimits;
        this.clock = clock;
    }

    @Transactional
    public WithdrawalResponse create(Long userId, Long companyId, WithdrawalRequest request) {
        Company company = companyService.getActiveOwned(userId, companyId);
        Partner partner = partnerService.get(companyId, request.partnerId());
        if (request.kind() == WithdrawalKind.DISTRIBUICAO_LUCROS) {
            return createDistribution(userId, company, partner, request);
        }
        YearMonth competence = request.competenceMonth();
        if (competence == null) {
            throw rule("Informe o mês da folha.");
        }
        requireProLaboreAllowed(companyId, competence);
        requirePaymentDate(request.paymentDate(), competence);
        if (withdrawals.existsByPartnerIdAndKindAndCompetenceMonth(partner.getId(), WithdrawalKind.PRO_LABORE, competence.atDay(1))) {
            throw new InvalidWithdrawalException("Já existe o pró-labore de " + MONTH.format(competence) + " para "
                    + partner.getName() + ".", true);
        }
        Taxes taxes = taxes(competence, request);
        try {
            Withdrawal withdrawal = withdrawals.saveAndFlush(new Withdrawal(userId, companyId, partner.getId(),
                    WithdrawalKind.PRO_LABORE, competence, request.paymentDate(), request.grossAmount(), taxes.inss(), taxes.irrf()));
            return response(withdrawal, taxes.calculated());
        } catch (DataIntegrityViolationException e) {
            throw new InvalidWithdrawalException("Já existe o pró-labore de " + MONTH.format(competence) + " para "
                    + partner.getName() + ".", true);
        }
    }

    /** Muda a data e o bruto; o INSS e o IRRF são recalculados (ou trocados pelos informados). */
    @Transactional
    public WithdrawalResponse update(Long userId, Long companyId, Long withdrawalId, WithdrawalRequest request) {
        Company company = companyService.getActiveOwned(userId, companyId);
        Withdrawal withdrawal = find(companyId, withdrawalId);
        if (request.kind() != withdrawal.getKind()) {
            throw rule("O tipo da retirada não muda: exclua e registre de novo.");
        }
        if (withdrawal.getKind() == WithdrawalKind.DISTRIBUICAO_LUCROS) {
            Taxes taxes = distributionTaxes(company, request);
            withdrawal.change(request.paymentDate(), request.grossAmount(), BigDecimal.ZERO, taxes.irrf());
            withdrawals.flush();
            syncPersonal(userId, withdrawal);
            return distributionResponse(userId, withdrawal);
        }
        YearMonth competence = withdrawal.getCompetenceMonth();
        requirePaymentDate(request.paymentDate(), competence);
        Taxes taxes = taxes(competence, request);
        withdrawal.change(request.paymentDate(), request.grossAmount(), taxes.inss(), taxes.irrf());
        syncPersonal(userId, withdrawal);
        return response(withdrawal, taxes.calculated());
    }

    /**
     * Exclui a retirada. Lançada nas finanças pessoais, a transação vai junto só com
     * {@code deletePersonal}; sem ele, fica, só perde a ligação (o front pergunta).
     */
    @Transactional
    public void delete(Long userId, Long companyId, Long withdrawalId, boolean deletePersonal) {
        companyService.getActiveOwned(userId, companyId);
        Withdrawal withdrawal = find(companyId, withdrawalId);
        Long transactionId = withdrawal.getPersonalTransactionId();
        withdrawals.delete(withdrawal);
        withdrawals.flush();
        if (transactionId != null && deletePersonal) {
            transactionService.delete(userId, transactionId);
        }
    }

    // A ponte com as finanças pessoais (Fase 6)

    static final String PRO_LABORE_CATEGORY = "Pró-labore";
    static final String PROFIT_CATEGORY = "Lucros distribuídos";

    /**
     * Lança o líquido da retirada como receita nas finanças pessoais, na data do pagamento. Só a do
     * sócio "sou eu". Sem categoria escolhida, usa "Pró-labore" ou "Lucros distribuídos", criada no
     * primeiro lançamento (ou a ativa com esse nome que o usuário já tenha).
     */
    @Transactional
    public WithdrawalResponse launch(Long userId, Long companyId, Long withdrawalId, Long categoryId) {
        Company company = companyService.getActiveOwned(userId, companyId);
        Withdrawal withdrawal = find(companyId, withdrawalId);
        if (!partnerService.get(companyId, withdrawal.getPartnerId()).isMe()) {
            throw rule("Só a retirada do sócio que é você entra nas suas finanças.");
        }
        if (withdrawal.getPersonalTransactionId() != null) {
            throw new InvalidWithdrawalException("Esta retirada já está lançada nas suas finanças.", true);
        }
        boolean proLabore = withdrawal.getKind() == WithdrawalKind.PRO_LABORE;
        Category category = categoryId != null
                ? categoryService.getActiveOwned(userId, categoryId)
                : categoryService.findOrCreateActive(userId, proLabore ? PRO_LABORE_CATEGORY : PROFIT_CATEGORY, CategoryType.INCOME,
                        proLabore ? CategoryColor.TEAL : CategoryColor.MINT, proLabore ? CategoryIcon.SALARY : CategoryIcon.INVESTMENT);
        if (category.getType() != CategoryType.INCOME) {
            throw rule("Escolha uma categoria de receita.");
        }
        Transaction transaction = transactionService.create(userId, category.getId(), null, withdrawal.getNetAmount(),
                withdrawal.getPaymentDate(), label(withdrawal, company.getName()));
        withdrawal.linkTo(transaction.getId());
        withdrawals.flush();
        return response(withdrawal, isCalculated(withdrawal.getCompetenceMonth()));
    }

    /** A transação excluída no extrato: a retirada fica, sem a ligação. */
    @Transactional
    public void releasePersonal(Long transactionId) {
        withdrawals.findByPersonalTransactionId(transactionId).ifPresent(withdrawal -> {
            withdrawal.unlink();
            withdrawals.flush();
        });
    }

    /** As origens das transações lançadas por retiradas, para o extrato mostrar e travar valor e data. */
    @Transactional(readOnly = true)
    public Map<Long, TransactionOrigin> originsOf(Long userId, java.util.Collection<Long> transactionIds) {
        Map<Long, TransactionOrigin> result = new HashMap<>();
        Map<Long, Company> companies = new HashMap<>();
        for (Withdrawal w : withdrawals.findAllByUserIdAndPersonalTransactionIdIn(userId, transactionIds)) {
            Company company = companies.computeIfAbsent(w.getCompanyId(), id -> companyService.getOwned(userId, id));
            result.put(w.getPersonalTransactionId(), new TransactionOrigin(TransactionOrigin.Kind.WITHDRAWAL,
                    label(w, company.getName()), company.getId(), w.getId(), !company.isArchived()));
        }
        return result;
    }

    private void syncPersonal(Long userId, Withdrawal withdrawal) {
        if (withdrawal.getPersonalTransactionId() != null) {
            transactionService.syncFromOrigin(userId, withdrawal.getPersonalTransactionId(), withdrawal.getNetAmount(),
                    withdrawal.getPaymentDate());
        }
    }

    /** "Pró-labore 10/2026 · Estúdio Ana" ou "Lucros · Estúdio Ana". */
    private static String label(Withdrawal withdrawal, String companyName) {
        return (withdrawal.getKind() == WithdrawalKind.PRO_LABORE
                ? "Pró-labore " + MONTH.format(withdrawal.getCompetenceMonth()) : "Lucros") + " · " + companyName;
    }

    /** As retiradas pagas no ano, da mais recente para a mais antiga. */
    @Transactional(readOnly = true)
    public List<WithdrawalResponse> list(Long userId, Long companyId, int year) {
        companyService.getOwned(userId, companyId);
        return withdrawals.findAllByCompanyIdAndPaymentDateBetweenOrderByPaymentDateDescIdDesc(companyId,
                        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).stream()
                .map(withdrawal -> response(withdrawal, isCalculated(withdrawal.getCompetenceMonth())))
                .toList();
    }

    /** O que saiu da conta em retiradas no período, pela data do pagamento. */
    public record PaidTotal(BigDecimal total, int count) {
    }

    @Transactional(readOnly = true)
    public PaidTotal paidBetween(Long companyId, LocalDate from, LocalDate to) {
        List<Withdrawal> paid = withdrawals.findAllByCompanyIdAndPaymentDateBetweenOrderByPaymentDateDescIdDesc(companyId, from, to);
        return new PaidTotal(paid.stream().map(Withdrawal::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add), paid.size());
    }

    /**
     * O líquido das retiradas pagas no período: o que o sócio recebeu. O INSS e o IRRF saem da conta
     * depois, no DARF, que vira uma despesa de imposto (decisão 29).
     */
    @Transactional(readOnly = true)
    public BigDecimal netPaidBetween(Long companyId, LocalDate from, LocalDate to) {
        return withdrawals.findAllByCompanyIdAndPaymentDateBetweenOrderByPaymentDateDescIdDesc(companyId, from, to).stream()
                .map(Withdrawal::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Distribuição de lucros

    private WithdrawalResponse createDistribution(Long userId, Company company, Partner partner, WithdrawalRequest request) {
        Taxes taxes = distributionTaxes(company, request);
        Withdrawal withdrawal = withdrawals.saveAndFlush(new Withdrawal(userId, company.getId(), partner.getId(),
                WithdrawalKind.DISTRIBUICAO_LUCROS, null, request.paymentDate(), request.grossAmount(), BigDecimal.ZERO, taxes.irrf()));
        return distributionResponse(userId, withdrawal);
    }

    /** Sem INSS (lucro não é salário) e com o IRRF que o usuário informar, se houve retenção. */
    private Taxes distributionTaxes(Company company, WithdrawalRequest request) {
        if (request.inssAmount() != null && request.inssAmount().signum() > 0) {
            throw rule("A distribuição de lucros não tem INSS.");
        }
        LocalDate date = request.paymentDate();
        if (date.isAfter(LocalDate.now(clock))) {
            throw rule("A data do pagamento não pode estar no futuro: a retirada registra o que já saiu da conta.");
        }
        if (date.isBefore(company.getOpeningDate())) {
            throw rule("A data do pagamento é anterior à abertura da empresa.");
        }
        BigDecimal irrf = request.irrfAmount() == null ? BigDecimal.ZERO : request.irrfAmount();
        if (irrf.compareTo(request.grossAmount()) >= 0) {
            throw rule("O IRRF não pode ser igual ou maior que o valor distribuído.");
        }
        return new Taxes(BigDecimal.ZERO, irrf, true);
    }

    /**
     * A resposta da distribuição com os avisos, que só saem ao salvar (o limite de isenção recalcula os
     * DAS do ano, caro demais para cada linha da lista).
     */
    private WithdrawalResponse distributionResponse(Long userId, Withdrawal withdrawal) {
        List<String> warnings = new ArrayList<>();
        Long companyId = withdrawal.getCompanyId();
        YearMonth month = YearMonth.from(withdrawal.getPaymentDate());

        BigDecimal partnerMonth = distributions(companyId, month.atDay(1), month.atEndOfMonth()).stream()
                .filter(w -> w.getPartnerId().equals(withdrawal.getPartnerId()))
                .map(Withdrawal::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        tableFor(month).ifPresent(table -> {
            BigDecimal suggested = ProfitDistribution.suggestedWithholding(partnerMonth, table);
            if (suggested.signum() > 0) {
                warnings.add("As distribuições de " + MONTH.format(month) + " a este sócio somam " + Money.brl(partnerMonth)
                        + ", acima de " + Money.brl(table.get(TaxParameter.DIVIDENDS_MONTHLY_EXEMPTION))
                        + ". Pela Lei 15.270/2025, há retenção de 10% de IR sobre o total (" + Money.brl(suggested)
                        + "); a Receita aplica também ao Simples, o que é contestado na Justiça. Lucros de resultados até "
                        + "2025, com distribuição aprovada até 31/12/2025, ficam de fora. Confirme com o seu contador e, se "
                        + "houve retenção, informe o IRRF.");
            }
        });

        TaxRegime regime = companyService.regimeIn(companyId, month).map(CompanyRegime::getTaxRegime).orElse(null);
        if (regime == TaxRegime.SIMPLES_NACIONAL || regime == TaxRegime.SIMEI) {
            exemptionLimits.getObject().limitUntil(userId, companyId, month).ifPresent(limit -> {
                BigDecimal year = distributions(companyId, LocalDate.of(month.getYear(), 1, 1), month.atEndOfMonth()).stream()
                        .map(Withdrawal::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (year.compareTo(limit) > 0) {
                    warnings.add("As distribuições de " + month.getYear() + " até " + MONTH.format(month) + " (" + Money.brl(year)
                            + ") passam do limite de isenção estimado sem contabilidade (" + Money.brl(limit) + "). Acima dele, "
                            + "só é isento se a contabilidade da empresa mostrar lucro maior: confirme com o seu contador.");
                }
            });
        } else if (regime != null) {
            warnings.add("No Lucro Presumido e no Real, o limite de isenção depende da apuração da empresa, que o app não "
                    + "calcula: confirme com o seu contador.");
        }
        return new WithdrawalResponse(withdrawal.getId(), withdrawal.getPartnerId(), withdrawal.getKind(), null,
                withdrawal.getPaymentDate(), withdrawal.getGrossAmount(), withdrawal.getInssAmount(), withdrawal.getIrrfAmount(),
                withdrawal.getNetAmount(), true, warnings, withdrawal.getPersonalTransactionId());
    }

    private List<Withdrawal> distributions(Long companyId, LocalDate from, LocalDate to) {
        return withdrawals.findAllByCompanyIdAndPaymentDateBetweenOrderByPaymentDateDescIdDesc(companyId, from, to).stream()
                .filter(w -> w.getKind() == WithdrawalKind.DISTRIBUICAO_LUCROS).toList();
    }

    // Pró-labore planejado

    /** Os planejados que valem hoje, um por sócio que tem plano. */
    @Transactional(readOnly = true)
    public List<ProLaborePlanResponse> plans(Long userId, Long companyId) {
        companyService.getOwned(userId, companyId);
        YearMonth now = YearMonth.now(clock);
        List<Long> partners = partnerService.list(userId, companyId).stream().map(Partner::getId).toList();
        return plans.findAllByPartnerIdIn(partners).stream()
                .filter(plan -> plan.isValidIn(now))
                .map(ProLaborePlanResponse::from)
                .toList();
    }

    /**
     * O planejado vale a partir do mês corrente, como o orçamento: os meses passados guardam o
     * planejado da época. Um plano que começou neste mês ainda não tem passado e é trocado no lugar.
     */
    @Transactional
    public ProLaborePlanResponse setPlan(Long userId, Long companyId, Long partnerId, BigDecimal amount) {
        companyService.getActiveOwned(userId, companyId);
        Partner partner = partnerService.get(companyId, partnerId);
        YearMonth now = YearMonth.now(clock);
        Optional<ProLaborePlan> current = plans.findByPartnerIdAndValidToIsNull(partner.getId());
        if (current.isPresent() && !current.get().getValidFrom().isBefore(now)) {
            current.get().replace(amount);
            return ProLaborePlanResponse.from(current.get());
        }
        current.ifPresent(plan -> {
            plan.close(now.minusMonths(1));
            plans.flush();
        });
        return ProLaborePlanResponse.from(plans.save(new ProLaborePlan(userId, partner.getId(), amount, now)));
    }

    @Transactional
    public void removePlan(Long userId, Long companyId, Long partnerId) {
        companyService.getActiveOwned(userId, companyId);
        Partner partner = partnerService.get(companyId, partnerId);
        YearMonth now = YearMonth.now(clock);
        plans.findByPartnerIdAndValidToIsNull(partner.getId()).ifPresent(plan -> {
            if (!plan.getValidFrom().isBefore(now)) {
                plans.delete(plan);
            } else {
                plan.close(now.minusMonths(1));
            }
        });
    }

    // Para outras features

    /** A folha de pró-labores por competência, para o Fator R. */
    @Transactional(readOnly = true)
    public Map<YearMonth, BigDecimal> proLaborePayroll(Long companyId, YearMonth from, YearMonth to) {
        return withdrawals.findAllByCompanyIdAndKindAndCompetenceMonthBetween(companyId, WithdrawalKind.PRO_LABORE,
                        from.atDay(1), to.atDay(1)).stream()
                .collect(Collectors.groupingBy(Withdrawal::getCompetenceMonth,
                        Collectors.reducing(BigDecimal.ZERO, Withdrawal::getGrossAmount, BigDecimal::add)));
    }

    /**
     * O pró-labore de cada sócio na competência: o registrado, se houver; senão, o planejado que valia,
     * quando {@code includePlanned} (a previsão do mês corrente e dos próximos). O planejado volta só
     * com o bruto: quem chama calcula o INSS e o IRRF com a tabela da competência.
     */
    @Transactional(readOnly = true)
    public List<ProLaboreSource> proLabores(Long companyId, YearMonth competence, boolean includePlanned) {
        List<ProLaboreSource> result = new ArrayList<>();
        Map<Long, Withdrawal> registered = new HashMap<>();
        for (Withdrawal w : withdrawals.findAllByCompanyIdAndKindAndCompetenceMonthBetween(companyId, WithdrawalKind.PRO_LABORE,
                competence.atDay(1), competence.atDay(1))) {
            registered.put(w.getPartnerId(), w);
            result.add(new ProLaboreSource(w.getPartnerId(), competence, w.getGrossAmount(), w.getInssAmount(),
                    w.getIrrfAmount(), true));
        }
        if (includePlanned) {
            List<Long> partners = partnerService.listOf(companyId).stream().map(Partner::getId).toList();
            for (ProLaborePlan plan : plans.findAllByPartnerIdIn(partners)) {
                if (!registered.containsKey(plan.getPartnerId()) && plan.isValidIn(competence)) {
                    result.add(new ProLaboreSource(plan.getPartnerId(), competence, plan.getGrossAmount(), null, null, false));
                }
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<WithdrawalResponse> listAll(Long userId) {
        return withdrawals.findAllByUserIdOrderByCompanyIdAscPaymentDateAscIdAsc(userId).stream()
                .map(w -> response(w, isCalculated(w.getCompetenceMonth()))).toList();
    }

    @Transactional(readOnly = true)
    public List<ProLaborePlanResponse> listAllPlans(Long userId) {
        return plans.findAllByUserIdOrderByPartnerIdAscValidFromAsc(userId).stream().map(ProLaborePlanResponse::from).toList();
    }

    /** Para a exclusão da conta, antes dos sócios e das empresas. */
    @Transactional
    public void deleteAllOf(Long userId) {
        withdrawals.deleteAllByUserId(userId);
        plans.deleteAllByUserId(userId);
    }

    private record Taxes(BigDecimal inss, BigDecimal irrf, boolean calculated) {
    }

    /** Nos meses que o app calcula, o motor decide; nos outros, valem os valores do holerite. */
    private Taxes taxes(YearMonth competence, WithdrawalRequest request) {
        Optional<TaxTable> table = tableFor(competence);
        if (table.isPresent()) {
            ProLaboreCalculator.Result result = ProLaboreCalculator.of(request.grossAmount(), table.get());
            return new Taxes(result.inss(), result.irrf(), true);
        }
        if (request.inssAmount() == null || request.irrfAmount() == null) {
            throw rule("O app não calcula impostos de " + MONTH.format(competence) + ". Informe o INSS e o IRRF retidos, "
                    + "como estão no holerite.");
        }
        if (request.inssAmount().add(request.irrfAmount()).compareTo(request.grossAmount()) >= 0) {
            throw rule("O INSS e o IRRF juntos não podem passar do valor bruto.");
        }
        return new Taxes(request.inssAmount(), request.irrfAmount(), false);
    }

    /** Os meses que o app calcula: com parâmetros e antes das regras de 2027. */
    private Optional<TaxTable> tableFor(YearMonth competence) {
        if (competence == null || !competence.isBefore(TaxRules.FIRST_UNSUPPORTED)) {
            return Optional.empty();
        }
        return taxParameters.findTableFor(competence);
    }

    private boolean isCalculated(YearMonth competence) {
        return tableFor(competence).isPresent();
    }

    private void requireProLaboreAllowed(Long companyId, YearMonth competence) {
        CompanyRegime regime = companyService.regimeIn(companyId, competence)
                .orElseThrow(() -> rule("O mês da folha é anterior à abertura da empresa."));
        if (regime.getLegalNature() == LegalNature.MEI) {
            throw rule("O MEI não tem pró-labore: a contribuição do titular já está no DAS-MEI. Registre a retirada como "
                    + "distribuição de lucros.");
        }
    }

    private void requirePaymentDate(LocalDate paymentDate, YearMonth competence) {
        if (paymentDate.isAfter(LocalDate.now(clock))) {
            throw rule("A data do pagamento não pode estar no futuro: a retirada registra o que já saiu da conta.");
        }
        if (paymentDate.isBefore(competence.atDay(1))) {
            throw rule("O pagamento não pode ser antes do mês da folha.");
        }
    }

    private WithdrawalResponse response(Withdrawal withdrawal, boolean calculated) {
        List<String> warnings = new ArrayList<>();
        YearMonth competence = withdrawal.getCompetenceMonth();
        if (competence != null && !YearMonth.from(withdrawal.getPaymentDate()).equals(competence)) {
            warnings.add("O pagamento foi em outro mês que o da folha. O IR é devido pelo mês do pagamento, e o app "
                    + "soma este IR ao DARF de " + MONTH.format(competence) + ": confira com o seu contador.");
        }
        tableFor(competence).ifPresent(table -> {
            if (ProLaboreCalculator.of(withdrawal.getGrossAmount(), table).belowMinimumWage()) {
                warnings.add("Abaixo do salário mínimo, a contribuição não conta como tempo de contribuição para a "
                        + "aposentadoria.");
            }
        });
        // A distribuição não tem o que calcular: "calculated" só é falso no pró-labore informado à mão.
        return new WithdrawalResponse(withdrawal.getId(), withdrawal.getPartnerId(), withdrawal.getKind(), competence,
                withdrawal.getPaymentDate(), withdrawal.getGrossAmount(), withdrawal.getInssAmount(), withdrawal.getIrrfAmount(),
                withdrawal.getNetAmount(), calculated || withdrawal.getKind() == WithdrawalKind.DISTRIBUICAO_LUCROS, warnings,
                withdrawal.getPersonalTransactionId());
    }

    private Withdrawal find(Long companyId, Long withdrawalId) {
        return withdrawals.findByIdAndCompanyId(withdrawalId, companyId).orElseThrow(WithdrawalNotFoundException::new);
    }

    private static InvalidWithdrawalException rule(String message) {
        return new InvalidWithdrawalException(message, false);
    }
}
