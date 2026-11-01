package com.akiratochiro.life_and_money_api.bill;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

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
    public ScheduledBillResponse create(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody CreateBillRequest request) {
        return billService.create(CurrentUser.id(jwt), request);
    }

    @GetMapping
    public List<ScheduledBillResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return billService.list(CurrentUser.id(jwt));
    }

    /** Vale do mês atual em diante: os meses passados continuam com o valor da época. */
    @PutMapping("/{id}")
    public ScheduledBillResponse update(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable Long id,
                                        @Valid @RequestBody UpdateBillRequest request) {
        return billService.update(CurrentUser.id(jwt), id, request);
    }

    /** Apaga a conta se ela nunca foi paga; se foi, encerra, e os meses passados ficam. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        billService.remove(CurrentUser.id(jwt), id);
    }
}
