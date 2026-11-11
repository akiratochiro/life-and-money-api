package com.akiratochiro.life_and_money_api.withdrawal;

import com.akiratochiro.life_and_money_api.company.CompanyRegime;
import com.akiratochiro.life_and_money_api.company.CompanyService;
import com.akiratochiro.life_and_money_api.company.LegalNature;
import com.akiratochiro.life_and_money_api.company.Partner;
import com.akiratochiro.life_and_money_api.company.PartnerService;
import com.akiratochiro.life_and_money_api.taxengine.ProLaboreCalculator;
import com.akiratochiro.life_and_money_api.taxengine.TaxRules;
import com.akiratochiro.life_and_money_api.taxengine.TaxTable;
import com.akiratochiro.life_and_money_api.taxparam.TaxParameterService;
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
 * Pró-labore (Fase 4) e, na Fase 6, distribuição de lucros. O pró-labore tem o INSS e o IRRF
 * calculados pelo motor tributário nos meses que o app calcula; nos outros (antes de 2026), eles são
 * informados como estão no holerite, para a folha do Fator R ter o histórico.
 */
@Service
public class WithdrawalService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final WithdrawalRepository withdrawals;
    private final ProLaborePlanRepository plans;
    private final CompanyService companyService;
    private final PartnerService partnerService;
    private final TaxParameterService taxParameters;
    private final Clock clock;

    public WithdrawalService(WithdrawalRepository withdrawals, ProLaborePlanRepository plans, CompanyService companyService,
                             PartnerService partnerService, TaxParameterService taxParameters, Clock clock) {
        this.withdrawals = withdrawals;
        this.plans = plans;
        this.companyService = companyService;
        this.partnerService = partnerService;
        this.taxParameters = taxParameters;
        this.clock = clock;
    }

    @Transactional
    public WithdrawalResponse create(Long userId, Long companyId, WithdrawalRequest request) {
        companyService.getActiveOwned(userId, companyId);
        if (request.kind() != WithdrawalKind.PRO_LABORE) {
            throw rule("A distribuição de lucros chega na próxima fase do app.");
        }
        Partner partner = partnerService.get(companyId, request.partnerId());
        YearMonth competence = request.competenceMonth();
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
        companyService.getActiveOwned(userId, companyId);
        Withdrawal withdrawal = find(companyId, withdrawalId);
        YearMonth competence = withdrawal.getCompetenceMonth();
        requirePaymentDate(request.paymentDate(), competence);
        Taxes taxes = taxes(competence, request);
        withdrawal.change(request.paymentDate(), request.grossAmount(), taxes.inss(), taxes.irrf());
        return response(withdrawal, taxes.calculated());
    }

    @Transactional
    public void delete(Long userId, Long companyId, Long withdrawalId) {
        companyService.getActiveOwned(userId, companyId);
        withdrawals.delete(find(companyId, withdrawalId));
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
            throw rule("O MEI não tem pró-labore: a contribuição do titular já está no DAS-MEI. A retirada como lucro "
                    + "chega na próxima fase do app.");
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
        return new WithdrawalResponse(withdrawal.getId(), withdrawal.getPartnerId(), withdrawal.getKind(), competence,
                withdrawal.getPaymentDate(), withdrawal.getGrossAmount(), withdrawal.getInssAmount(), withdrawal.getIrrfAmount(),
                withdrawal.getNetAmount(), calculated, warnings);
    }

    private Withdrawal find(Long companyId, Long withdrawalId) {
        return withdrawals.findByIdAndCompanyId(withdrawalId, companyId).orElseThrow(WithdrawalNotFoundException::new);
    }

    private static InvalidWithdrawalException rule(String message) {
        return new InvalidWithdrawalException(message, false);
    }
}
