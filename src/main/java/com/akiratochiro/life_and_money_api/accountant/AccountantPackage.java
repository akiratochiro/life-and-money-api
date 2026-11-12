package com.akiratochiro.life_and_money_api.accountant;

import com.akiratochiro.life_and_money_api.companyexpense.ExpenseMonthResponse;
import com.akiratochiro.life_and_money_api.invoice.Invoice;
import com.akiratochiro.life_and_money_api.tax.TaxOccurrenceResponse;
import com.akiratochiro.life_and_money_api.withdrawal.WithdrawalResponse;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Tudo o que o pacote precisa, levantado antes do primeiro byte (decisão 33): se algo falhar, ainda dá
 * para responder um erro normal. Os arquivos ficam só como ids, lidos um de cada vez na escrita.
 */
record AccountantPackage(
        Long userId,
        Long companyId,
        String folder,
        YearMonth month,
        List<Invoice> invoices,
        List<ExpenseMonthResponse.Item> expenses,
        Map<Long, String> categoryNames,
        List<TaxOccurrenceResponse> taxes,
        List<WithdrawalResponse> withdrawals,
        Map<Long, String> partnerNames,
        byte[] summaryPdf
) {
    String zipName() {
        return folder + ".zip";
    }
}
