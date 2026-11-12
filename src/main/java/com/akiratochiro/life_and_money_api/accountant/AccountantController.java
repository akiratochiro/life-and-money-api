package com.akiratochiro.life_and_money_api.accountant;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;

@RestController
public class AccountantController {

    private final AccountantService service;

    public AccountantController(AccountantService service) {
        this.service = service;
    }

    /**
     * O ZIP do mês, escrito direto na resposta (decisão 33). Tudo o que pode falhar acontece no
     * {@code prepare}, antes do primeiro byte; o download é sempre {@code attachment} com {@code nosniff}.
     */
    @GetMapping("/companies/{companyId}/accountant-package")
    @Operation(operationId = "downloadAccountantPackage", summary = "O pacote do mês para o contador (ZIP: notas, comprovantes, CSVs e resumo em PDF)")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/zip", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<StreamingResponseBody> download(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                                          @RequestParam YearMonth month) {
        AccountantPackage pkg = service.prepare(CurrentUser.id(jwt), companyId, month);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pkg.zipName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(out -> service.write(pkg, out));
    }
}
