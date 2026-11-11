package com.akiratochiro.life_and_money_api.invoice;

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
import java.util.List;

@RestController
@RequestMapping("/companies/{companyId}/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping(path = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "previewInvoices",
            summary = "Lê até 10 arquivos (XML ou PDF) e diz o que encontrou em cada um; não grava nada")
    public List<InvoicePreview> preview(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                        @RequestPart("files") List<MultipartFile> files) {
        return invoiceService.preview(CurrentUser.id(jwt), companyId, files.stream().map(InvoiceController::upload).toList());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createInvoice",
            summary = "Salva uma nota: do XML, com os valores do arquivo; do PDF ou manual, com os campos confirmados em data")
    public InvoiceResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                  @RequestPart("file") MultipartFile file,
                                  @Valid @RequestPart(value = "data", required = false) InvoiceData data) {
        return InvoiceResponse.from(invoiceService.save(CurrentUser.id(jwt), companyId, upload(file), data));
    }

    @GetMapping
    @Operation(operationId = "listInvoices", summary = "As notas do ano, pela competência, da mais recente para a mais antiga")
    public List<InvoiceResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                      @RequestParam int year) {
        return invoiceService.list(CurrentUser.id(jwt), companyId, year).stream().map(InvoiceResponse::from).toList();
    }

    @GetMapping("/years")
    @Operation(operationId = "listInvoiceYears", summary = "Os anos que têm nota, do mais recente ao mais antigo")
    public List<Integer> years(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId) {
        return invoiceService.years(CurrentUser.id(jwt), companyId);
    }

    @GetMapping("/{invoiceId}")
    @Operation(operationId = "getInvoice")
    public InvoiceResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long invoiceId) {
        return InvoiceResponse.from(invoiceService.get(CurrentUser.id(jwt), companyId, invoiceId));
    }

    @PutMapping("/{invoiceId}")
    @Operation(operationId = "correctInvoice", summary = "Corrige os campos de uma nota do PDF ou manual")
    public InvoiceResponse correct(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                   @PathVariable Long invoiceId, @Valid @RequestBody InvoiceData data) {
        return InvoiceResponse.from(invoiceService.correct(CurrentUser.id(jwt), companyId, invoiceId, data));
    }

    @PostMapping("/{invoiceId}/cancel")
    @Operation(operationId = "cancelInvoice", summary = "Marca a nota como cancelada: ela deixa de contar como receita")
    public InvoiceResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long invoiceId) {
        return InvoiceResponse.from(invoiceService.cancel(CurrentUser.id(jwt), companyId, invoiceId));
    }

    @PostMapping("/{invoiceId}/reactivate")
    @Operation(operationId = "reactivateInvoice")
    public InvoiceResponse reactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long invoiceId) {
        return InvoiceResponse.from(invoiceService.reactivate(CurrentUser.id(jwt), companyId, invoiceId));
    }

    @DeleteMapping("/{invoiceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteInvoice", summary = "Apaga a nota e o arquivo dela")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId, @PathVariable Long invoiceId) {
        invoiceService.delete(CurrentUser.id(jwt), companyId, invoiceId);
    }

    /**
     * O arquivo original, sempre como download ({@code attachment}) e com {@code nosniff}: o
     * navegador nunca interpreta dentro do nosso domínio um arquivo que veio do usuário.
     */
    @GetMapping("/{invoiceId}/file")
    @Operation(operationId = "downloadInvoiceFile", summary = "Baixa o XML ou PDF original da nota")
    public ResponseEntity<byte[]> file(@AuthenticationPrincipal Jwt jwt, @PathVariable Long companyId,
                                       @PathVariable Long invoiceId) {
        Attachment attachment = invoiceService.file(CurrentUser.id(jwt), companyId, invoiceId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(attachment.getFilename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(attachment.getContent());
    }

    private static Upload upload(MultipartFile file) {
        try {
            return new Upload(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
