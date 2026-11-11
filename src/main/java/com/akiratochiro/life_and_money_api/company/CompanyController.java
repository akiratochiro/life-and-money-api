package com.akiratochiro.life_and_money_api.company;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createCompany",
            summary = "Cadastra a empresa, o enquadramento a partir do mês da abertura e você como sócio")
    public CompanyResponse create(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody CreateCompanyRequest request) {
        return CompanyResponse.from(companyService.create(CurrentUser.id(jwt), request.toNewCompany()));
    }

    @GetMapping
    @Operation(operationId = "listCompanies", summary = "Empresas em ordem alfabética")
    public List<CompanyResponse> list(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(defaultValue = "false") boolean includeArchived) {
        return companyService.list(CurrentUser.id(jwt), includeArchived).stream()
                .map(CompanyResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getCompany")
    public CompanyResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return CompanyResponse.from(companyService.get(CurrentUser.id(jwt), id));
    }

    @PatchMapping("/{id}")
    @Operation(operationId = "updateCompany", summary = "Edita os dados cadastrais; o que não vier fica como está")
    public CompanyResponse update(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable Long id,
                                  @Valid @RequestBody UpdateCompanyRequest request) {
        return CompanyResponse.from(companyService.update(CurrentUser.id(jwt), id, request.toChanges()));
    }

    @PostMapping("/{id}/archive")
    @Operation(operationId = "archiveCompany", summary = "Arquiva a empresa, preservando todo o histórico")
    public CompanyResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return CompanyResponse.from(companyService.archive(CurrentUser.id(jwt), id));
    }

    @PostMapping("/{id}/unarchive")
    @Operation(operationId = "unarchiveCompany")
    public CompanyResponse unarchive(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return CompanyResponse.from(companyService.unarchive(CurrentUser.id(jwt), id));
    }
}
