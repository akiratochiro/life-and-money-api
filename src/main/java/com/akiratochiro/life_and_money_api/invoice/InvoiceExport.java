package com.akiratochiro.life_and_money_api.invoice;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Uma nota no export da conta: os dados e a empresa. O arquivo original fica de fora (baixável na tela). */
public record InvoiceExport(
        @Schema(requiredMode = REQUIRED) Long companyId,
        @Schema(requiredMode = REQUIRED) InvoiceResponse invoice
) {
    static InvoiceExport from(Invoice invoice) {
        return new InvoiceExport(invoice.getCompanyId(), InvoiceResponse.from(invoice));
    }
}
