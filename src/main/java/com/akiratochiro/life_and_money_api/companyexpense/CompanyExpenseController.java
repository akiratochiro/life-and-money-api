package com.akiratochiro.life_and_money_api.companyexpense;

import com.akiratochiro.life_and_money_api.attachment.Attachment;
import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/companies/{companyId}")
public class CompanyExpenseController {

    private final CompanyExpenseService service;

    public CompanyExpenseController(CompanyExpenseService service) {
        this.service = service;
    }

    @GetMapping("/expenses")
    @Operation(operationId = "companyExpenseMonth", summary = "O mês inteiro: avulsas, fixas pagas e pendentes, com os totais")
    public ExpenseMonthResponse month(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                      @RequestParam YearMonth month) {
        return service.month(CurrentUser.id(jwt), companyId, month);
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createCompanyExpense", summary = "Despesa avulsa, já paga")
    public ExpenseResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                  @Valid @RequestBody OneOffExpenseRequest request) {
        return ExpenseResponse.from(service.createOneOff(CurrentUser.id(jwt), companyId, request));
    }

    @PutMapping("/expenses/{expenseId}")
    @Operation(operationId = "updateCompanyExpense", summary = "Edita a despesa; no pagamento de uma fixa, só o valor e a data")
    public ExpenseResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long expenseId,
                                  @Valid @RequestBody OneOffExpenseRequest request) {
        return ExpenseResponse.from(service.update(CurrentUser.id(jwt), companyId, expenseId, request));
    }

    @DeleteMapping("/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteCompanyExpense", summary = "Apaga a despesa e o comprovante; o mês de uma fixa volta a pendente")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long expenseId) {
        service.delete(CurrentUser.id(jwt), companyId, expenseId);
    }

    @PutMapping(path = "/expenses/{expenseId}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "attachExpenseReceipt", summary = "Anexa ou troca o comprovante (PDF, JPEG ou PNG, até 2 MB)")
    public ExpenseResponse attachReceipt(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                         @PathVariable Long expenseId, @RequestPart("file") MultipartFile file) {
        try {
            return ExpenseResponse.from(service.attachReceipt(CurrentUser.id(jwt), companyId, expenseId,
                    file.getOriginalFilename(), file.getBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Sempre como download e com nosniff, como o arquivo das notas. */
    @GetMapping("/expenses/{expenseId}/receipt")
    @Operation(operationId = "downloadExpenseReceipt")
    public ResponseEntity<byte[]> receipt(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                          @PathVariable Long expenseId) {
        Attachment attachment = service.receipt(CurrentUser.id(jwt), companyId, expenseId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(attachment.getFilename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(attachment.getContent());
    }

    @DeleteMapping("/expenses/{expenseId}/receipt")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "removeExpenseReceipt")
    public void removeReceipt(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long expenseId) {
        service.removeReceipt(CurrentUser.id(jwt), companyId, expenseId);
    }

    @GetMapping("/recurring-expenses")
    @Operation(operationId = "listRecurringExpenses", summary = "Despesas fixas com os termos atuais")
    public List<RecurringExpenseResponse> listRecurring(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId) {
        return service.listRecurring(CurrentUser.id(jwt), companyId);
    }

    @PostMapping("/recurring-expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createRecurringExpense")
    public RecurringExpenseResponse createRecurring(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                                    @Valid @RequestBody RecurringExpenseRequest request) {
        return service.createRecurring(CurrentUser.id(jwt), companyId, request);
    }

    @PutMapping("/recurring-expenses/{recurringId}")
    @Operation(operationId = "updateRecurringExpense", summary = "Novos termos a partir deste mês; os meses passados não mudam")
    public RecurringExpenseResponse updateRecurring(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                                    @PathVariable Long recurringId,
                                                    @Valid @RequestBody RecurringExpenseRequest request) {
        return service.updateRecurring(CurrentUser.id(jwt), companyId, recurringId, request);
    }

    @DeleteMapping("/recurring-expenses/{recurringId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteRecurringExpense", summary = "Apaga se nunca foi paga; senão, encerra no mês anterior")
    public void deleteRecurring(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long recurringId) {
        service.deleteRecurring(CurrentUser.id(jwt), companyId, recurringId);
    }

    @PostMapping("/recurring-expenses/{recurringId}/occurrences/{month}/payment")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "payRecurringExpense", summary = "Paga o mês; sem valor, o previsto; sem data, o vencimento")
    public ExpenseResponse pay(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                               @PathVariable Long recurringId, @PathVariable YearMonth month,
                               @Valid @RequestBody(required = false) PayRecurringExpenseRequest request) {
        return ExpenseResponse.from(service.pay(CurrentUser.id(jwt), companyId, recurringId, month, request));
    }

    @DeleteMapping("/recurring-expenses/{recurringId}/occurrences/{month}/payment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "unpayRecurringExpense", summary = "Desfaz o pagamento: o mês volta a pendente")
    public void unpay(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long recurringId,
                      @PathVariable YearMonth month) {
        service.unpay(CurrentUser.id(jwt), companyId, recurringId, month);
    }
}
