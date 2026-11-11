package com.akiratochiro.life_and_money_api.invoice;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class RevenueController {

    private final RevenueService revenueService;

    public RevenueController(RevenueService revenueService) {
        this.revenueService = revenueService;
    }

    @GetMapping("/companies/{companyId}/revenue")
    @Operation(operationId = "companyRevenue",
            summary = "Receita por competência (notas ativas), por dia, semana ISO ou mês, com zero nos períodos vazios")
    public RevenueResponse revenue(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                   @RequestParam LocalDate from, @RequestParam LocalDate to,
                                   @RequestParam RevenueGranularity granularity) {
        return revenueService.revenue(CurrentUser.id(jwt), companyId, from, to, granularity);
    }
}
