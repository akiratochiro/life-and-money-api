package com.akiratochiro.life_and_money_api.accountant;

import com.akiratochiro.life_and_money_api.companyexpense.ExpenseMonthResponse;
import com.akiratochiro.life_and_money_api.companyoverview.CompanyOverviewResponse;
import com.akiratochiro.life_and_money_api.invoice.Invoice;
import com.akiratochiro.life_and_money_api.invoice.InvoiceStatus;
import com.akiratochiro.life_and_money_api.tax.TaxOccurrenceResponse;
import com.akiratochiro.life_and_money_api.withdrawal.WithdrawalKind;
import com.akiratochiro.life_and_money_api.withdrawal.WithdrawalResponse;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * O resumo do mês em PDF (decisão 33), com as fontes padrão do PDF (Helvetica, sem embutir nada). Os
 * números são os da visão geral do mês, para o resultado bater com o card.
 */
final class SummaryPdf {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR);
    private static final DateTimeFormatter COMPETENCE = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font TEXT = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8);

    private SummaryPdf() {
    }

    record Header(String companyName, String cnpj, String regime, YearMonth month, LocalDateTime generatedAt) {
    }

    static byte[] build(Header header, CompanyOverviewResponse overview, List<Invoice> invoices,
                        List<ExpenseMonthResponse.Item> paidExpenses, Map<Long, String> categoryNames,
                        List<TaxOccurrenceResponse> taxes, List<WithdrawalResponse> withdrawals, Map<Long, String> partnerNames) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(document, out);
        document.open();

        document.add(new Paragraph(header.companyName() + (header.cnpj() != null ? " · CNPJ " + header.cnpj() : ""), TITLE));
        document.add(new Paragraph(header.regime(), TEXT));
        document.add(new Paragraph("Pacote do contador · " + MONTH.format(header.month()) + " · gerado em "
                + GENERATED.format(header.generatedAt()), TEXT));

        // Receita
        long active = invoices.stream().filter(i -> i.getStatus() == InvoiceStatus.ATIVA).count();
        long cancelled = invoices.size() - active;
        section(document, "Receita (notas por competência)");
        PdfPTable revenue = table(2, 80, 20);
        row(revenue, TEXT, active + (active == 1 ? " nota ativa" : " notas ativas")
                + (cancelled > 0 ? " · " + cancelled + (cancelled == 1 ? " cancelada" : " canceladas") + " (fora do total)" : ""),
                money(overview.revenue()));
        BigDecimal[] withheld = new BigDecimal[6];
        java.util.Arrays.fill(withheld, BigDecimal.ZERO);
        for (Invoice invoice : invoices) {
            if (invoice.getStatus() != InvoiceStatus.ATIVA) continue;
            withheld[0] = withheld[0].add(invoice.getIssWithheldAmount());
            withheld[1] = withheld[1].add(invoice.getIrrfWithheldAmount());
            withheld[2] = withheld[2].add(invoice.getPisWithheldAmount());
            withheld[3] = withheld[3].add(invoice.getCofinsWithheldAmount());
            withheld[4] = withheld[4].add(invoice.getCsllWithheldAmount());
            withheld[5] = withheld[5].add(invoice.getInssWithheldAmount());
        }
        String[] names = {"ISS", "IRRF", "PIS", "COFINS", "CSLL", "INSS"};
        StringBuilder retained = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (withheld[i].signum() > 0) {
                retained.append(retained.isEmpty() ? "Retido pelos tomadores: " : " · ").append(names[i]).append(' ').append(money(withheld[i]));
            }
        }
        if (!retained.isEmpty()) {
            row(revenue, SMALL, retained.toString(), "");
        }
        document.add(revenue);

        // Despesas por categoria
        section(document, "Despesas pagas do mês, por categoria");
        Map<String, BigDecimal> byCategory = new TreeMap<>(java.text.Collator.getInstance(PT_BR));
        BigDecimal paidTotal = BigDecimal.ZERO;
        for (ExpenseMonthResponse.Item item : paidExpenses) {
            byCategory.merge(categoryNames.getOrDefault(item.categoryId(), "Sem categoria"), item.amount(), BigDecimal::add);
            paidTotal = paidTotal.add(item.amount());
        }
        PdfPTable expenses = table(2, 80, 20);
        if (byCategory.isEmpty()) {
            row(expenses, TEXT, "Nenhuma despesa paga no mês.", "");
        }
        byCategory.forEach((category, amount) -> row(expenses, TEXT, category, money(amount)));
        row(expenses, BOLD, "Total pago", money(paidTotal));
        document.add(expenses);
        document.add(new Paragraph("A categoria Impostos são os pagamentos de impostos feitos no mês; no resultado, os impostos "
                + "entram pela competência, uma vez só.", SMALL));

        // Impostos
        section(document, "Impostos da competência");
        PdfPTable tax = table(5, 40, 15, 15, 15, 15);
        row(tax, BOLD, "Imposto", "Vencimento", "Previsto", "Pago", "Diferença");
        if (taxes.isEmpty()) {
            row(tax, TEXT, "Nenhum imposto nesta competência.", "", "", "", "");
        }
        for (TaxOccurrenceResponse t : taxes) {
            TaxOccurrenceResponse.Payment p = t.payment();
            row(tax, TEXT, t.name(), t.dueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    t.expectedAmount() == null ? "sem previsto" : money(t.expectedAmount()),
                    p != null && p.paidAmount() != null ? money(p.paidAmount()) : "—",
                    p != null && p.difference() != null ? money(p.difference()) : p == null ? "a pagar" : "");
        }
        document.add(tax);

        // Retiradas
        section(document, "Retiradas pagas no mês");
        PdfPTable out2 = table(5, 40, 15, 15, 15, 15);
        row(out2, BOLD, "Sócio", "Bruto", "INSS", "IRRF", "Líquido");
        if (withdrawals.isEmpty()) {
            row(out2, TEXT, "Nenhuma retirada paga no mês.", "", "", "", "");
        }
        for (WithdrawalResponse w : withdrawals) {
            String what = w.kind() == WithdrawalKind.PRO_LABORE
                    ? "pró-labore " + (w.competenceMonth() == null ? "" : COMPETENCE.format(w.competenceMonth())) : "lucros";
            row(out2, TEXT, partnerNames.getOrDefault(w.partnerId(), "Sócio") + " · " + what, money(w.grossAmount()),
                    money(w.inssAmount()), money(w.irrfAmount()), money(w.netAmount()));
        }
        document.add(out2);

        // Resultado
        section(document, "Resultado do mês");
        PdfPTable result = table(2, 80, 20);
        row(result, TEXT, "Receita", money(overview.revenue()));
        row(result, TEXT, "Despesas (sem a categoria Impostos" + (overview.expenses().pending().signum() > 0
                ? "; inclui " + money(overview.expenses().pending()) + " de fixas não pagas, pelo previsto)" : ")"),
                money(overview.expenses().total()));
        row(result, TEXT, "Impostos da competência (pago, senão previsto)", money(overview.taxes().total()));
        row(result, BOLD, "Resultado (receita - despesas - impostos)", money(overview.result()));
        document.add(result);

        document.add(new Paragraph(" ", TEXT));
        document.add(new Paragraph("Estimativa do app a partir dos lançamentos: confira com os documentos do pacote.", SMALL));
        document.close();
        return out.toByteArray();
    }

    private static void section(Document document, String title) {
        Paragraph paragraph = new Paragraph(title, HEADING);
        paragraph.setSpacingBefore(12);
        paragraph.setSpacingAfter(4);
        document.add(paragraph);
    }

    private static PdfPTable table(int columns, float... widths) {
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        return table;
    }

    private static void row(PdfPTable table, Font font, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(cells[i], font));
            cell.setBorder(PdfPCell.BOTTOM);
            cell.setBorderColor(java.awt.Color.LIGHT_GRAY);
            cell.setPadding(3);
            cell.setHorizontalAlignment(i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            table.addCell(cell);
        }
    }

    private static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(value).replace(' ', ' ');
    }
}
