package com.akiratochiro.life_and_money_api.taxparam;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Os parâmetros são públicos para quem está logado: é a lei, não dado do usuário. */
@RestController
public class TaxParameterController {

    private final TaxParameterService service;

    public TaxParameterController(TaxParameterService service) {
        this.service = service;
    }

    @GetMapping("/tax-parameters")
    @Operation(operationId = "taxParameters", summary = "Os parâmetros tributários que valem na data, com a fonte legal de cada um")
    public TaxParametersResponse describe(@RequestParam LocalDate date) {
        return service.describe(date);
    }
}
