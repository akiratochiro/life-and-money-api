package com.akiratochiro.life_and_money_api.company;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * O faturamento esperado do mês (Fase 8, decisão 30). Vale para qualquer enquadramento: hoje o monitor
 * do Fator R usa, e a Fase 10 (teto de faturamento) vai usar também.
 */
@Service
public class ExpectedRevenueService {

    private final ExpectedRevenueRepository repository;
    private final CompanyService companyService;
    private final Clock clock;

    public ExpectedRevenueService(ExpectedRevenueRepository repository, CompanyService companyService, Clock clock) {
        this.repository = repository;
        this.companyService = companyService;
        this.clock = clock;
    }

    /** O valor que vale hoje, se houver. */
    @Transactional(readOnly = true)
    public Optional<ExpectedRevenueResponse> current(Long userId, Long companyId) {
        companyService.getOwned(userId, companyId);
        return validIn(companyId, YearMonth.now(clock)).map(ExpectedRevenueResponse::from);
    }

    /** O valor que valia num mês, para a projeção; sem dono conferido (quem chama já conferiu). */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> amountIn(Long companyId, YearMonth month) {
        return validIn(companyId, month).map(ExpectedRevenue::getAmount);
    }

    /**
     * Vale a partir do mês corrente, como o pró-labore planejado: os meses passados guardam o valor da
     * época. Um valor que começou neste mês ainda não tem passado e é trocado no lugar.
     */
    @Transactional
    public ExpectedRevenueResponse set(Long userId, Long companyId, BigDecimal amount) {
        companyService.getActiveOwned(userId, companyId);
        YearMonth now = YearMonth.now(clock);
        Optional<ExpectedRevenue> current = repository.findByCompanyIdAndValidToIsNull(companyId);
        if (current.isPresent() && !current.get().getValidFrom().isBefore(now)) {
            current.get().replace(amount);
            return ExpectedRevenueResponse.from(current.get());
        }
        current.ifPresent(revenue -> {
            revenue.close(now.minusMonths(1));
            repository.flush();
        });
        return ExpectedRevenueResponse.from(repository.save(new ExpectedRevenue(userId, companyId, amount, now)));
    }

    /** Encerra no mês anterior; o que começou neste mês é apagado. */
    @Transactional
    public void remove(Long userId, Long companyId) {
        companyService.getActiveOwned(userId, companyId);
        YearMonth now = YearMonth.now(clock);
        repository.findByCompanyIdAndValidToIsNull(companyId).ifPresent(revenue -> {
            if (!revenue.getValidFrom().isBefore(now)) {
                repository.delete(revenue);
            } else {
                revenue.close(now.minusMonths(1));
            }
        });
    }

    private Optional<ExpectedRevenue> validIn(Long companyId, YearMonth month) {
        return repository.findAllByCompanyId(companyId).stream().filter(revenue -> revenue.isValidIn(month)).findFirst();
    }

    // Conta

    @Transactional(readOnly = true)
    public List<ExpectedRevenueResponse> listAll(Long userId) {
        return repository.findAllByUserIdOrderByCompanyIdAscValidFromAsc(userId).stream().map(ExpectedRevenueResponse::from).toList();
    }

    /** Para a exclusão da conta: antes das empresas, por causa da chave estrangeira. */
    @Transactional
    public void deleteAllOf(Long userId) {
        repository.deleteAllByUserId(userId);
    }
}
