package com.akiratochiro.life_and_money_api.withdrawal;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/companies/{companyId}")
public class WithdrawalController {

    private final WithdrawalService service;

    public WithdrawalController(WithdrawalService service) {
        this.service = service;
    }

    @GetMapping("/withdrawals")
    @Operation(operationId = "listWithdrawals", summary = "Retiradas pagas no ano, da mais recente para a mais antiga")
    public List<WithdrawalResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @RequestParam int year) {
        return service.list(CurrentUser.id(jwt), companyId, year);
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createWithdrawal", summary = "Registra um pró-labore pago, com o INSS e o IRRF calculados")
    public WithdrawalResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                     @Valid @RequestBody WithdrawalRequest request) {
        return service.create(CurrentUser.id(jwt), companyId, request);
    }

    @PutMapping("/withdrawals/{withdrawalId}")
    @Operation(operationId = "updateWithdrawal", summary = "Muda a data e o bruto; o sócio e o mês da folha não mudam")
    public WithdrawalResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                     @PathVariable Long withdrawalId, @Valid @RequestBody WithdrawalRequest request) {
        return service.update(CurrentUser.id(jwt), companyId, withdrawalId, request);
    }

    @DeleteMapping("/withdrawals/{withdrawalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteWithdrawal",
            summary = "Exclui a retirada; com deletePersonal, também o lançamento nas finanças pessoais (sem ele, o lançamento fica)")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long withdrawalId,
                       @RequestParam(defaultValue = "false") boolean deletePersonal) {
        service.delete(CurrentUser.id(jwt), companyId, withdrawalId, deletePersonal);
    }

    @PostMapping("/withdrawals/{withdrawalId}/personal-transaction")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "launchWithdrawal", summary = "Lança o líquido da retirada do \"sou eu\" como receita nas finanças pessoais")
    public WithdrawalResponse launch(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long withdrawalId,
                                     @RequestBody(required = false) LaunchRequest request) {
        return service.launch(CurrentUser.id(jwt), companyId, withdrawalId, request == null ? null : request.categoryId());
    }

    @GetMapping("/pro-labore-plans")
    @Operation(operationId = "listProLaborePlans", summary = "O pró-labore planejado de cada sócio, como vale hoje")
    public List<ProLaborePlanResponse> plans(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId) {
        return service.plans(CurrentUser.id(jwt), companyId);
    }

    @PutMapping("/partners/{partnerId}/pro-labore-plan")
    @Operation(operationId = "setProLaborePlan", summary = "Define o pró-labore planejado a partir deste mês")
    public ProLaborePlanResponse setPlan(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                         @PathVariable Long partnerId, @Valid @RequestBody ProLaborePlanRequest request) {
        return service.setPlan(CurrentUser.id(jwt), companyId, partnerId, request.grossAmount());
    }

    @DeleteMapping("/partners/{partnerId}/pro-labore-plan")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "removeProLaborePlan", summary = "Encerra o pró-labore planejado a partir deste mês")
    public void removePlan(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long partnerId) {
        service.removePlan(CurrentUser.id(jwt), companyId, partnerId);
    }
}
