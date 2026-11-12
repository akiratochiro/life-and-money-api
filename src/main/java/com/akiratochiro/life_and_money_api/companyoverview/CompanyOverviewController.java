package com.akiratochiro.life_and_money_api.companyoverview;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import com.akiratochiro.life_and_money_api.tax.FatorRMonitor;
import com.akiratochiro.life_and_money_api.tax.FatorRMonitorResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/companies/{companyId}")
public class CompanyOverviewController {

    private final CompanyOverviewService service;
    private final FatorRMonitor fatorRMonitor;

    public CompanyOverviewController(CompanyOverviewService service, FatorRMonitor fatorRMonitor) {
        this.service = service;
        this.fatorRMonitor = fatorRMonitor;
    }

    @GetMapping("/overview")
    @Operation(operationId = "companyOverview", summary = "Os cards do mês: receita, despesas, impostos da competência, resultado e retiradas")
    public CompanyOverviewResponse overview(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @RequestParam YearMonth month) {
        return service.overview(CurrentUser.id(jwt), companyId, month);
    }

    @GetMapping("/cash")
    @Operation(operationId = "companyCash", summary = "O caixa de hoje: saldo da conta PJ, provisão de impostos e saldo disponível")
    public CompanyCashResponse cash(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId) {
        return service.cash(CurrentUser.id(jwt), companyId);
    }

    @GetMapping("/fator-r")
    @Operation(operationId = "companyFatorR", summary = "O monitor do Fator R: o da próxima competência, com o mês corrente projetado")
    public FatorRMonitorResponse fatorR(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId) {
        return fatorRMonitor.monitor(CurrentUser.id(jwt), companyId);
    }

    @GetMapping("/upcoming")
    @Operation(operationId = "companyUpcoming", summary = "Impostos e despesas fixas atrasados (até 3 meses) e dos próximos 30 dias")
    public List<UpcomingItem> upcoming(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId) {
        return service.upcoming(CurrentUser.id(jwt), companyId);
    }
}
