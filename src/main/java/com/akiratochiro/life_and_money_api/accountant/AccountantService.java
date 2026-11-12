package com.akiratochiro.life_and_money_api.accountant;

import com.akiratochiro.life_and_money_api.attachment.Attachment;
import com.akiratochiro.life_and_money_api.company.AnnexConfig;
import com.akiratochiro.life_and_money_api.company.Cnpj;
import com.akiratochiro.life_and_money_api.company.Company;
import com.akiratochiro.life_and_money_api.company.CompanyRegime;
import com.akiratochiro.life_and_money_api.company.CompanyService;
import com.akiratochiro.life_and_money_api.company.Partner;
import com.akiratochiro.life_and_money_api.company.PartnerService;
import com.akiratochiro.life_and_money_api.companyexpense.CompanyExpenseCategory;
import com.akiratochiro.life_and_money_api.companyexpense.CompanyExpenseCategoryService;
import com.akiratochiro.life_and_money_api.companyexpense.CompanyExpenseService;
import com.akiratochiro.life_and_money_api.companyexpense.ExpenseMonthResponse;
import com.akiratochiro.life_and_money_api.companyexpense.ExpenseStatus;
import com.akiratochiro.life_and_money_api.companyoverview.CompanyOverviewService;
import com.akiratochiro.life_and_money_api.invoice.InvalidRevenueRangeException;
import com.akiratochiro.life_and_money_api.invoice.Invoice;
import com.akiratochiro.life_and_money_api.invoice.InvoiceService;
import com.akiratochiro.life_and_money_api.invoice.InvoiceStatus;
import com.akiratochiro.life_and_money_api.ratelimit.RateLimit;
import com.akiratochiro.life_and_money_api.ratelimit.RateLimiter;
import com.akiratochiro.life_and_money_api.tax.TaxOccurrenceResponse;
import com.akiratochiro.life_and_money_api.tax.TaxService;
import com.akiratochiro.life_and_money_api.withdrawal.WithdrawalKind;
import com.akiratochiro.life_and_money_api.withdrawal.WithdrawalResponse;
import com.akiratochiro.life_and_money_api.withdrawal.WithdrawalService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * O pacote mensal para o contador (Fase 11, decisão 33). {@link #prepare} levanta e confere tudo antes
 * do primeiro byte; {@link #write} escreve o ZIP direto na resposta, lendo um anexo por vez (cada leitura
 * é uma transação curta do serviço dono, e o open-in-view está desligado), então a memória nunca guarda
 * mais que um arquivo.
 */
@Service
public class AccountantService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final CompanyService companyService;
    private final PartnerService partnerService;
    private final InvoiceService invoiceService;
    private final CompanyExpenseService expenseService;
    private final CompanyExpenseCategoryService categoryService;
    private final TaxService taxService;
    private final WithdrawalService withdrawalService;
    private final CompanyOverviewService overviewService;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public AccountantService(CompanyService companyService, PartnerService partnerService, InvoiceService invoiceService,
                             CompanyExpenseService expenseService, CompanyExpenseCategoryService categoryService, TaxService taxService,
                             WithdrawalService withdrawalService, CompanyOverviewService overviewService, RateLimiter rateLimiter,
                             Clock clock) {
        this.companyService = companyService;
        this.partnerService = partnerService;
        this.invoiceService = invoiceService;
        this.expenseService = expenseService;
        this.categoryService = categoryService;
        this.taxService = taxService;
        this.withdrawalService = withdrawalService;
        this.overviewService = overviewService;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** Dono, mês, limite de uso, dados e o PDF: tudo o que pode falhar, antes de a resposta começar. */
    public AccountantPackage prepare(Long userId, Long companyId, YearMonth month) {
        // Primeiro o limite, por usuário: como na consulta de CNPJ, ele vale para qualquer pedido de quem chama.
        rateLimiter.consume(RateLimit.ACCOUNTANT_PACKAGE_PER_USER, String.valueOf(userId));
        Company company = companyService.getOwned(userId, companyId);
        YearMonth start = YearMonth.from(company.getOpeningDate());
        YearMonth current = YearMonth.now(clock);
        if (month.isBefore(start) || month.isAfter(current)) {
            throw new InvalidRevenueRangeException("O pacote vai da abertura da empresa (" + start.format(DateTimeFormatter.ofPattern("MM/yyyy"))
                    + ") até o mês corrente.");
        }

        List<Invoice> invoices = invoiceService.list(userId, companyId, month.getYear()).stream()
                .filter(invoice -> YearMonth.from(invoice.getCompetenceDate()).equals(month))
                .sorted(Comparator.comparing(Invoice::getCompetenceDate).thenComparing(Invoice::getId))
                .toList();
        List<ExpenseMonthResponse.Item> expenses = expenseService.month(userId, companyId, month).items().stream()
                .filter(item -> item.status() == ExpenseStatus.PAID)
                .sorted(Comparator.comparing(ExpenseMonthResponse.Item::date).thenComparing(ExpenseMonthResponse.Item::name))
                .toList();
        Map<Long, String> categories = categoryService.list(userId, companyId, true).stream()
                .collect(Collectors.toMap(CompanyExpenseCategory::getId, CompanyExpenseCategory::getName));
        List<TaxOccurrenceResponse> taxes = taxService.ofCompetence(userId, companyId, month);
        List<WithdrawalResponse> withdrawals = withdrawalService.list(userId, companyId, month.getYear()).stream()
                .filter(w -> YearMonth.from(w.paymentDate()).equals(month))
                .sorted(Comparator.comparing(WithdrawalResponse::paymentDate))
                .toList();
        Map<Long, String> partners = partnerService.list(userId, companyId).stream()
                .collect(Collectors.toMap(Partner::getId, Partner::getName));

        String regime = companyService.regimeIn(companyId, month).map(AccountantService::regimeLabel).orElse("");
        byte[] pdf = SummaryPdf.build(
                new SummaryPdf.Header(company.getName(), company.getCnpj() == null ? null : new Cnpj(company.getCnpj()).formatted(),
                        regime, month, LocalDateTime.now(clock)),
                overviewService.overview(userId, companyId, month), invoices, expenses, categories, taxes, withdrawals, partners);
        String folder = FileNames.slug(company.getName()) + "_" + month;
        return new AccountantPackage(userId, companyId, folder, month, invoices, expenses, categories, taxes, withdrawals, partners, pdf);
    }

    /** Escreve o ZIP: os arquivos primeiro, um por vez, e depois os CSVs (que citam os nomes) e o resumo. */
    public void write(AccountantPackage pkg, OutputStream out) throws IOException {
        String root = pkg.folder() + "/";
        FileNames names = new FileNames();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            Map<Long, String> invoiceFiles = new HashMap<>();
            for (Invoice invoice : pkg.invoices()) {
                Attachment file = invoiceService.file(pkg.userId(), pkg.companyId(), invoice.getId());
                String folder = invoice.getStatus() == InvoiceStatus.CANCELADA ? "notas/canceladas/" : "notas/";
                String name = names.unique(folder, invoice.getCompetenceDate().format(DAY) + "_nota-" + invoice.getNumber() + "_"
                        + invoice.getTakerName(), extension(file));
                put(zip, root + name, file.getContent());
                invoiceFiles.put(invoice.getId(), name);
            }
            Map<Long, String> receipts = new HashMap<>();
            for (ExpenseMonthResponse.Item item : pkg.expenses()) {
                if (item.hasReceipt() && item.expenseId() != null) {
                    Attachment file = expenseService.receipt(pkg.userId(), pkg.companyId(), item.expenseId());
                    String name = names.unique("despesas/", item.date().format(DAY) + "_" + item.name(), extension(file));
                    put(zip, root + name, file.getContent());
                    receipts.put(item.expenseId(), name);
                }
            }
            put(zip, root + "notas.csv", invoicesCsv(pkg, invoiceFiles));
            put(zip, root + "despesas.csv", expensesCsv(pkg, receipts));
            put(zip, root + "impostos.csv", taxesCsv(pkg));
            put(zip, root + "retiradas.csv", withdrawalsCsv(pkg));
            put(zip, root + "resumo-" + pkg.month() + ".pdf", pkg.summaryPdf());
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
        zip.flush();
    }

    static String extension(Attachment file) {
        return switch (file.getContentType()) {
            case "application/pdf" -> "pdf";
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "application/xml", "text/xml" -> "xml";
            default -> {
                String name = file.getFilename() == null ? "" : file.getFilename();
                int dot = name.lastIndexOf('.');
                String ext = dot < 0 ? "" : FileNames.slug(name.substring(dot + 1));
                yield ext.isEmpty() || ext.equals("arquivo") ? "bin" : ext;
            }
        };
    }

    private static byte[] invoicesCsv(AccountantPackage pkg, Map<Long, String> files) {
        Csv csv = new Csv("Número", "Chave de acesso", "Emissão", "Competência", "Tomador", "CPF/CNPJ do tomador", "Valor do serviço",
                "Desconto incondicional", "ISS retido", "IRRF retido", "PIS retido", "COFINS retido", "CSLL retido", "INSS retido",
                "Líquido", "Status", "Arquivo");
        for (Invoice i : pkg.invoices()) {
            csv.row(i.getNumber(), i.getAccessKey(), i.getIssueDate(), i.getCompetenceDate(), i.getTakerName(), i.getTakerDocument(),
                    i.getServiceAmount(), i.getUnconditionalDiscountAmount(), i.getIssWithheldAmount(), i.getIrrfWithheldAmount(),
                    i.getPisWithheldAmount(), i.getCofinsWithheldAmount(), i.getCsllWithheldAmount(), i.getInssWithheldAmount(),
                    i.getNetAmount(), i.getStatus() == InvoiceStatus.ATIVA ? "ativa" : "cancelada", files.get(i.getId()));
        }
        return csv.toBytes();
    }

    private static byte[] expensesCsv(AccountantPackage pkg, Map<Long, String> receipts) {
        Csv csv = new Csv("Data do pagamento", "Despesa", "Categoria", "Valor", "Fixa", "Comprovante");
        for (ExpenseMonthResponse.Item e : pkg.expenses()) {
            csv.row(e.date(), e.name(), pkg.categoryNames().get(e.categoryId()), e.amount(),
                    e.kind() == ExpenseMonthResponse.Kind.RECURRING, receipts.get(e.expenseId()));
        }
        return csv.toBytes();
    }

    private static byte[] taxesCsv(AccountantPackage pkg) {
        Csv csv = new Csv("Imposto", "Competência", "Vencimento", "Previsto", "Pago", "Diferença", "Data do pagamento", "Status");
        for (TaxOccurrenceResponse t : pkg.taxes()) {
            TaxOccurrenceResponse.Payment p = t.payment();
            csv.row(t.name(), t.competence().format(DateTimeFormatter.ofPattern("MM/yyyy")), t.dueDate(), t.expectedAmount(),
                    p == null ? null : p.paidAmount(), p == null ? null : p.difference(), p == null ? null : p.paidDate(),
                    switch (t.status()) {
                        case PAID -> "pago";
                        case PENDING -> "pendente";
                        case OVERDUE -> "atrasado";
                    });
        }
        return csv.toBytes();
    }

    private static byte[] withdrawalsCsv(AccountantPackage pkg) {
        Csv csv = new Csv("Data do pagamento", "Sócio", "Tipo", "Competência", "Bruto", "INSS", "IRRF", "Líquido");
        for (WithdrawalResponse w : pkg.withdrawals()) {
            csv.row(w.paymentDate(), pkg.partnerNames().get(w.partnerId()),
                    w.kind() == WithdrawalKind.PRO_LABORE ? "pró-labore" : "distribuição de lucros",
                    w.competenceMonth() == null ? null : w.competenceMonth().format(DateTimeFormatter.ofPattern("MM/yyyy")),
                    w.grossAmount(), w.inssAmount(), w.irrfAmount(), w.netAmount());
        }
        return csv.toBytes();
    }

    static String regimeLabel(CompanyRegime regime) {
        return switch (regime.getTaxRegime()) {
            case SIMEI -> "MEI (Simei)";
            case SIMPLES_NACIONAL -> "Simples Nacional" + (regime.getAnnexConfig() == AnnexConfig.FATOR_R
                    ? " (Anexo pelo Fator R)" : " (Anexo III)");
            case LUCRO_PRESUMIDO -> "Lucro Presumido";
            case LUCRO_REAL -> "Lucro Real";
        };
    }
}
