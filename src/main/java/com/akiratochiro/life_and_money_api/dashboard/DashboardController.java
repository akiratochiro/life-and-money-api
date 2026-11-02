package com.akiratochiro.life_and_money_api.dashboard;


import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService){
        this.dashboardService = dashboardService;
    }

    @GetMapping("/expenses-by-category")
    public ExpensesByCategoryResponse expensesByCategory(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam YearMonth month){

        return dashboardService.expensesByCategory(CurrentUser.id(jwt), month);
    }

    @GetMapping("/monthly-totals")
    public MonthlyTotalsResponse monthlyTotals(@AuthenticationPrincipal Jwt jwt,
                                               @RequestParam(defaultValue = "6") @Min(1) @Max(24) int months){
        return dashboardService.monthlyTotals(CurrentUser.id(jwt), months);
    }

    @GetMapping("/budget")
    public BudgetDashboardResponse budgetDashboard(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam YearMonth month){
        return dashboardService.budget(CurrentUser.id(jwt), month);
    }


}
