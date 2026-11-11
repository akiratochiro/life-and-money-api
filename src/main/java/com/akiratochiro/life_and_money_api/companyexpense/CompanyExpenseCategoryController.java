package com.akiratochiro.life_and_money_api.companyexpense;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/companies/{companyId}/expense-categories")
public class CompanyExpenseCategoryController {

    private final CompanyExpenseCategoryService service;

    public CompanyExpenseCategoryController(CompanyExpenseCategoryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(operationId = "listExpenseCategories", summary = "Categorias de despesa da empresa, em ordem alfabética")
    public List<ExpenseCategoryResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                              @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.list(CurrentUser.id(jwt), companyId, includeArchived).stream().map(ExpenseCategoryResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createExpenseCategory")
    public ExpenseCategoryResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                          @Valid @RequestBody ExpenseCategoryRequest request) {
        return ExpenseCategoryResponse.from(service.create(CurrentUser.id(jwt), companyId, request.name(), request.color(),
                request.icon(), Boolean.TRUE.equals(request.countsAsPayroll())));
    }

    @PatchMapping("/{categoryId}")
    @Operation(operationId = "updateExpenseCategory", summary = "Edita nome, cor, ícone ou se conta como folha")
    public ExpenseCategoryResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                          @PathVariable Long categoryId, @Valid @RequestBody UpdateExpenseCategoryRequest request) {
        return ExpenseCategoryResponse.from(service.update(CurrentUser.id(jwt), companyId, categoryId, request.name(),
                request.color(), request.icon(), request.countsAsPayroll()));
    }

    @PostMapping("/{categoryId}/archive")
    @Operation(operationId = "archiveExpenseCategory", summary = "Arquiva a categoria e encerra as despesas fixas dela")
    public ExpenseCategoryResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                           @PathVariable Long categoryId) {
        return ExpenseCategoryResponse.from(service.archive(CurrentUser.id(jwt), companyId, categoryId));
    }
}
