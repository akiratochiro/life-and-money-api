package com.akiratochiro.life_and_money_api.support;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SupportController {

    private final SupportService service;

    public SupportController(SupportService service) {
        this.service = service;
    }

    @PostMapping("/support")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "contactSupport", summary = "Manda uma mensagem ao suporte; a resposta chega no e-mail da conta")
    public void contact(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SupportRequest request) {
        service.send(CurrentUser.id(jwt), request);
    }
}
