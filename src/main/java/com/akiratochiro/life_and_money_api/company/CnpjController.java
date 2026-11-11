package com.akiratochiro.life_and_money_api.company;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CnpjController {

    private final CnpjService cnpjService;

    public CnpjController(CnpjService cnpjService) {
        this.cnpjService = cnpjService;
    }

    /** O CNPJ vai sem a barra ("12ABC34501DE35"), que num caminho de URL separaria segmentos. */
    @GetMapping("/cnpj/{cnpj}")
    @Operation(operationId = "lookupCnpj",
            summary = "Dados públicos do CNPJ para pré-preencher o cadastro; nunca falha por causa da BrasilAPI")
    public CnpjLookupResponse lookup(@AuthenticationPrincipal Jwt jwt, @PathVariable String cnpj) {
        return cnpjService.lookup(CurrentUser.id(jwt), cnpj);
    }
}
