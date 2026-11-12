package com.akiratochiro.life_and_money_api.company;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/companies/{companyId}/expected-revenue")
public class ExpectedRevenueController {

    private final ExpectedRevenueService service;

    public ExpectedRevenueController(ExpectedRevenueService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(operationId = "getExpectedRevenue", summary = "O faturamento esperado do mês que vale hoje; 204 se não houver")
    public ResponseEntity<ExpectedRevenueResponse> current(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId) {
        return service.current(CurrentUser.id(jwt), companyId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    @Operation(operationId = "setExpectedRevenue", summary = "Define o faturamento esperado do mês a partir deste mês")
    public ExpectedRevenueResponse set(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                       @Valid @RequestBody ExpectedRevenueRequest request) {
        return service.set(CurrentUser.id(jwt), companyId, request.amount());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "removeExpectedRevenue", summary = "Encerra o faturamento esperado a partir deste mês")
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId) {
        service.remove(CurrentUser.id(jwt), companyId);
    }
}
