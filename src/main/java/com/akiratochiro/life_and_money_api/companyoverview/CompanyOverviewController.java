package com.akiratochiro.life_and_money_api.companyoverview;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
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

    public CompanyOverviewController(CompanyOverviewService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @Operation(operationId = "companyOverview", summary = "Os cards do mês: receita, despesas, impostos da competência, resultado e retiradas")
    public CompanyOverviewResponse overview(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @RequestParam YearMonth month) {
        return service.overview(CurrentUser.id(jwt), companyId, month);
    }

    @GetMapping("/upcoming")
    @Operation(operationId = "companyUpcoming", summary = "Impostos e despesas fixas atrasados (até 3 meses) e dos próximos 30 dias")
    public List<UpcomingItem> upcoming(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId) {
        return service.upcoming(CurrentUser.id(jwt), companyId);
    }
}
