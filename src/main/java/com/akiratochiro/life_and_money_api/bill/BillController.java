package com.akiratochiro.life_and_money_api.bill;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/bills")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createBill", summary = "Cria uma conta a pagar ou a receber")
    public ScheduledBillResponse create(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody CreateBillRequest request) {
        return billService.create(CurrentUser.id(jwt), request);
    }

    @GetMapping
    @Operation(operationId = "listBills", summary = "As contas, com os termos em vigor")
    public List<ScheduledBillResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return billService.list(CurrentUser.id(jwt));
    }

    @GetMapping("/occurrences")
    @Operation(operationId = "billOccurrences", summary = "Ocorrências do mês", description = "Calculadas a partir das contas, com o status de cada uma")
    public List<BillOccurrenceResponse> occurrences(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestParam YearMonth month) {
        return billService.occurrencesIn(CurrentUser.id(jwt), month);
    }

    @GetMapping("/upcoming")
    @Operation(operationId = "upcomingBills", summary = "O que pede atenção",
            description = "Atrasadas dos últimos 12 meses e o que vence de hoje até daqui a 7 dias, sem as pagas")
    public List<BillOccurrenceResponse> upcoming(@AuthenticationPrincipal Jwt jwt) {
        return billService.upcoming(CurrentUser.id(jwt));
    }

    @PostMapping("/{id}/occurrences/{month}/payment")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "payBill", summary = "Marca a ocorrência como paga",
            description = "Cria a transação real. Sem corpo, usa o valor previsto e a data de hoje")
    public BillOccurrenceResponse pay(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable Long id,
                                      @Parameter(example = "2026-11") @PathVariable YearMonth month,
                                      @Valid @RequestBody(required = false) PayBillRequest request) {
        return billService.pay(CurrentUser.id(jwt), id, month, request);
    }

    @DeleteMapping("/{id}/occurrences/{month}/payment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "undoBillPayment", summary = "Desfaz o pagamento", description = "Apaga a transação criada ao pagar")
    public void undoPayment(@AuthenticationPrincipal Jwt jwt,
                            @PathVariable Long id,
                            @Parameter(example = "2026-11") @PathVariable YearMonth month) {
        billService.undoPayment(CurrentUser.id(jwt), id, month);
    }

    /** Vale do mês atual em diante: os meses passados continuam com o valor da época. */
    @PutMapping("/{id}")
    @Operation(operationId = "updateBill", summary = "Muda a conta do mês atual em diante")
    public ScheduledBillResponse update(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable Long id,
                                        @Valid @RequestBody UpdateBillRequest request) {
        return billService.update(CurrentUser.id(jwt), id, request);
    }

    /** Apaga a conta se ela nunca foi paga; se foi, encerra, e os meses passados ficam. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "removeBill", summary = "Apaga a conta, ou a encerra se já teve pagamento")
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        billService.remove(CurrentUser.id(jwt), id);
    }
}
