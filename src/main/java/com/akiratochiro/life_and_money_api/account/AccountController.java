package com.akiratochiro.life_and_money_api.account;

import com.akiratochiro.life_and_money_api.auth.AccountCredentials;
import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
public class AccountController {

    private final AccountService accountService;
    private final AccountCredentials accountCredentials;

    AccountController(AccountService accountService, AccountCredentials accountCredentials) {
        this.accountService = accountService;
        this.accountCredentials = accountCredentials;
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui a conta e todos os dados dela",
            description = "Exige a senha atual. Apaga categorias, transações, orçamento, metas e sessões "
                    + "numa única transação, e não tem volta. Senha errada responde 403 (não 401, que o "
                    + "front trata como sessão encerrada); são 5 tentativas a cada 15 minutos.")
    public void deleteAccount(@AuthenticationPrincipal Jwt jwt,
                              @Valid @RequestBody DeleteAccountRequest request,
                              HttpServletResponse response) {
        accountService.deleteAccount(CurrentUser.id(jwt), request.password());
        response.addHeader(HttpHeaders.SET_COOKIE, accountCredentials.expiredSessionCookie());
    }
}
