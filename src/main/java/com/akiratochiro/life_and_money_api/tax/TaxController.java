package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/companies/{companyId}")
public class TaxController {

    private final TaxService service;

    public TaxController(TaxService service) {
        this.service = service;
    }

    @GetMapping("/taxes")
    @Operation(operationId = "taxMonth", summary = "Os vencimentos do mês, com o previsto, a memória de cálculo e o pagamento")
    public TaxMonthResponse month(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @RequestParam YearMonth month) {
        return service.month(CurrentUser.id(jwt), companyId, month);
    }

    @PostMapping("/taxes/{obligationId}/occurrences/{competence}/payment")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "payTax", summary = "Paga o imposto (vira despesa de impostos) ou marca a declaração como entregue")
    public TaxOccurrenceResponse pay(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long obligationId,
                                     @PathVariable YearMonth competence, @Valid @RequestBody PayTaxRequest request) {
        return service.pay(CurrentUser.id(jwt), companyId, obligationId, competence, request);
    }

    @DeleteMapping("/taxes/{obligationId}/occurrences/{competence}/payment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "unpayTax", summary = "Desfaz o pagamento ou a entrega; a despesa de impostos some junto")
    public void unpay(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long obligationId,
                      @PathVariable YearMonth competence) {
        service.unpay(CurrentUser.id(jwt), companyId, obligationId, competence);
    }

    @GetMapping("/obligations")
    @Operation(operationId = "listObligations", summary = "As obrigações do sistema e as personalizadas")
    public List<ObligationResponse> obligations(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId) {
        return service.obligations(CurrentUser.id(jwt), companyId);
    }

    @PostMapping("/obligations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createObligation", summary = "Cria uma obrigação personalizada")
    public ObligationResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                     @Valid @RequestBody ObligationRequest request) {
        return service.createCustom(CurrentUser.id(jwt), companyId, request);
    }

    public record ObligationUpdate(
            @Schema(description = "Ativar ou desativar; vale para todas") Boolean active,
            @Schema(description = "Só nas personalizadas: os novos campos") @Valid ObligationRequest changes
    ) {
    }

    @PatchMapping("/obligations/{obligationId}")
    @Operation(operationId = "updateObligation", summary = "Ativa ou desativa; nas personalizadas, muda os campos")
    public ObligationResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long obligationId,
                                     @Valid @RequestBody ObligationUpdate request) {
        return service.update(CurrentUser.id(jwt), companyId, obligationId, request.active(), request.changes());
    }

    @DeleteMapping("/obligations/{obligationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteObligation", summary = "Exclui uma personalizada sem pagamentos")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long obligationId) {
        service.deleteCustom(CurrentUser.id(jwt), companyId, obligationId);
    }

    @GetMapping("/pro-labore-calculator")
    @Operation(operationId = "proLaboreCalculator",
            summary = "Pró-labore para o Fator R atingir a meta e o custo do Anexo III contra o V, com o faturamento informado")
    public ProLaboreCalculatorResponse calculator(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                                  @RequestParam BigDecimal monthlyRevenue,
                                                  @RequestParam(defaultValue = "0") BigDecimal otherPayroll) {
        return service.proLaboreCalculator(CurrentUser.id(jwt), companyId, monthlyRevenue, otherPayroll);
    }
}
