package com.akiratochiro.life_and_money_api.account;

import com.akiratochiro.life_and_money_api.auth.AccountCredentials;
import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;

@RestController
@RequestMapping("/users/me")
public class AccountController {

    private final AccountService accountService;
    private final AccountCredentials accountCredentials;

    AccountController(AccountService accountService, AccountCredentials accountCredentials) {
        this.accountService = accountService;
        this.accountCredentials = accountCredentials;
    }

    @GetMapping("/export")
    @Operation(summary = "Exporta todos os dados da conta em JSON",
            description = "Perfil, categorias (inclusive arquivadas), transações, histórico do orçamento e "
                    + "metas, no mesmo formato das outras respostas. Vem como anexo para download.")
    public ResponseEntity<AccountExport> export(@AuthenticationPrincipal Jwt jwt) {
        AccountExport export = accountService.export(CurrentUser.id(jwt));
        String date = export.exportedAt().atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("life-and-money-" + date + ".json")
                        .build()
                        .toString())
                .body(export);
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
