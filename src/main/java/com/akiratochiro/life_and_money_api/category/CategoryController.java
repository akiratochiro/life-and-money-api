package com.akiratochiro.life_and_money_api.category;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody CreateCategoryRequest request) {
        Category category = categoryService.create(
                CurrentUser.id(jwt), request.name(), request.type(), request.color(), request.icon());
        return CategoryResponse.from(category);
    }

    @GetMapping
    public List<CategoryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(defaultValue = "false") boolean includeArchived) {
        return categoryService.list(CurrentUser.id(jwt), includeArchived).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /** Renomeia, troca a cor ou o ícone; o que não vier no corpo fica como está. */
    @PatchMapping("/{id}")
    @Operation(operationId = "updateCategory", summary = "Edita nome, cor ou ícone da categoria")
    public CategoryResponse update(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable Long id,
                                   @Valid @RequestBody UpdateCategoryRequest request) {
        Category category = categoryService.update(
                CurrentUser.id(jwt), id, request.name(), request.color(), request.icon());
        return CategoryResponse.from(category);
    }

    @PostMapping("/{id}/archive")
    public CategoryResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Category category = categoryService.archive(CurrentUser.id(jwt), id);
        return CategoryResponse.from(category);
    }
}
