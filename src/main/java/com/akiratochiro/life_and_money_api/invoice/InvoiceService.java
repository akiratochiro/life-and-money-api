package com.akiratochiro.life_and_money_api.invoice;

import com.akiratochiro.life_and_money_api.attachment.Attachment;
import com.akiratochiro.life_and_money_api.attachment.AttachmentService;
import com.akiratochiro.life_and_money_api.attachment.FileType;
import com.akiratochiro.life_and_money_api.attachment.InvalidAttachmentException;
import com.akiratochiro.life_and_money_api.company.Cnpj;
import com.akiratochiro.life_and_money_api.company.Company;
import com.akiratochiro.life_and_money_api.company.CompanyService;
import com.akiratochiro.life_and_money_api.company.InvalidCompanyDataException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * As notas fiscais da empresa, em duas etapas (decisão 20): a pré-visualização lê os arquivos e
 * diz o que encontrou, sem gravar nada; salvar é um segundo pedido, arquivo por arquivo. Assim uma
 * pré-visualização abandonada não deixa arquivo órfão, e não precisa de job de limpeza.
 */
@Service
public class InvoiceService {

    static final Set<FileType> ACCEPTED = Set.of(FileType.XML, FileType.PDF);
    static final int MAX_FILES = 10;
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InvoiceRepository repository;
    private final CompanyService companyService;
    private final AttachmentService attachmentService;
    private final Clock clock;

    public InvoiceService(InvoiceRepository repository, CompanyService companyService,
                          AttachmentService attachmentService, Clock clock) {
        this.repository = repository;
        this.companyService = companyService;
        this.attachmentService = attachmentService;
        this.clock = clock;
    }

    /** Lê até 10 arquivos e diz, de cada um, se pode entrar e com que dados. Não grava nada. */
    @Transactional(readOnly = true)
    public List<InvoicePreview> preview(Long userId, Long companyId, List<Upload> files) {
        Company company = companyService.getActiveOwned(userId, companyId);
        if (files.isEmpty()) {
            throw new InvalidInvoiceException("Escolha pelo menos um arquivo.");
        }
        if (files.size() > MAX_FILES) {
            throw new InvalidInvoiceException("Envie no máximo " + MAX_FILES + " arquivos por vez.");
        }
        return files.stream().map(file -> previewOne(company, file)).toList();
    }

    private InvoicePreview previewOne(Company company, Upload file) {
        FileType type;
        try {
            type = AttachmentService.check(file.filename(), file.content(), ACCEPTED);
        } catch (InvalidAttachmentException e) {
            return InvoicePreview.rejected(file.filename(), null, e.getMessage());
        }

        if (type == FileType.PDF) {
            return previewPdf(company, file);
        }

        ParsedInvoice parsed;
        try {
            parsed = NfseXmlParser.parse(file.content());
            checkXmlRules(company, parsed);
        } catch (InvoiceParseException | InvalidInvoiceException e) {
            return InvoicePreview.rejected(file.filename(), type, e.getMessage());
        }
        Optional<Invoice> duplicate = repository.findByCompanyIdAndAccessKey(company.getId(), parsed.accessKey());
        if (duplicate.isPresent()) {
            return new InvoicePreview(file.filename(), type, InvoicePreview.Status.DUPLICATE,
                    duplicateMessage(duplicate.get()), List.of(), InvoiceData.from(InvoiceFields.from(parsed)));
        }
        return new InvoicePreview(file.filename(), type, InvoicePreview.Status.READY, null,
                warnings(company, parsed), InvoiceData.from(InvoiceFields.from(parsed)));
    }

    private InvoicePreview previewPdf(Company company, Upload file) {
        Optional<ParsedInvoice> parsed = DanfsePdfParser.parse(file.content());
        if (parsed.isEmpty()) {
            return new InvoicePreview(file.filename(), FileType.PDF, InvoicePreview.Status.NEEDS_INPUT,
                    "Não reconhecemos os dados deste PDF. Preencha os campos à mão; o arquivo fica anexado à nota.",
                    List.of(), null);
        }
        ParsedInvoice found = parsed.get();
        if (found.accessKey() != null) {
            Optional<Invoice> duplicate = repository.findByCompanyIdAndAccessKey(company.getId(), found.accessKey());
            if (duplicate.isPresent()) {
                return new InvoicePreview(file.filename(), FileType.PDF, InvoicePreview.Status.DUPLICATE,
                        duplicateMessage(duplicate.get()), List.of(), InvoiceData.from(InvoiceFields.from(found)));
            }
        }
        return new InvoicePreview(file.filename(), FileType.PDF, InvoicePreview.Status.NEEDS_INPUT,
                "Confira os campos lidos do PDF e complete o que faltar antes de salvar.",
                List.of(), InvoiceData.from(InvoiceFields.from(found)));
    }

    /**
     * Salva uma nota. Do XML, o servidor relê o arquivo e usa só os valores dele: os campos enviados
     * são ignorados, porque os valores de uma nota assinada não mudam. Do PDF ou à mão, valem os
     * campos que o usuário confirmou.
     */
    @Transactional
    public Invoice save(Long userId, Long companyId, Upload file, InvoiceData confirmed) {
        Company company = companyService.getActiveOwned(userId, companyId);
        FileType type = AttachmentService.check(file.filename(), file.content(), ACCEPTED);

        InvoiceFields fields;
        InvoiceSource source;
        String replacedKey = null;
        if (type == FileType.XML) {
            ParsedInvoice parsed = NfseXmlParser.parse(file.content());
            checkXmlRules(company, parsed);
            fields = InvoiceFields.from(parsed);
            source = InvoiceSource.XML;
            replacedKey = parsed.replacedAccessKey();
        } else {
            if (confirmed == null) {
                throw new InvalidInvoiceException("Confira os campos da nota antes de salvar.");
            }
            fields = confirmed.toFields();
            checkTypedRules(company, fields);
            source = DanfsePdfParser.parse(file.content()).isPresent() ? InvoiceSource.PDF : InvoiceSource.MANUAL;
        }

        if (fields.accessKey() != null) {
            repository.findByCompanyIdAndAccessKey(companyId, fields.accessKey()).ifPresent(existing -> {
                throw new DuplicateInvoiceException(duplicateMessage(existing));
            });
        }

        Attachment attachment = attachmentService.store(userId, file.filename(), file.content(), ACCEPTED);
        Invoice invoice;
        try {
            // saveAndFlush: o UNIQUE do banco barra a mesma nota enviada em dois cliques simultâneos,
            // e o erro precisa aparecer aqui para virar a mesma mensagem amigável.
            invoice = repository.saveAndFlush(new Invoice(userId, companyId, attachment.getId(), source, fields, replacedKey));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateInvoiceException("Você já cadastrou esta nota.");
        }

        // A substituta cancela a substituída: legalmente, a anterior foi cancelada pela substituição.
        if (replacedKey != null) {
            repository.findByCompanyIdAndAccessKey(companyId, replacedKey).ifPresent(Invoice::cancel);
        }
        return invoice;
    }

    /** As notas do ano, pela competência, da mais recente para a mais antiga. */
    @Transactional(readOnly = true)
    public List<Invoice> list(Long userId, Long companyId, int year) {
        companyService.getOwned(userId, companyId);
        return repository.findAllByCompanyIdAndCompetenceDateBetweenOrderByCompetenceDateDescIdDesc(
                companyId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    @Transactional(readOnly = true)
    public List<Integer> years(Long userId, Long companyId) {
        companyService.getOwned(userId, companyId);
        return repository.findYearsByCompanyId(companyId);
    }

    @Transactional(readOnly = true)
    public Invoice get(Long userId, Long companyId, Long invoiceId) {
        companyService.getOwned(userId, companyId);
        return find(companyId, invoiceId);
    }

    /** Corrige os campos de uma nota do PDF ou manual. A do XML só muda de status. */
    @Transactional
    public Invoice correct(Long userId, Long companyId, Long invoiceId, InvoiceData data) {
        Company company = companyService.getActiveOwned(userId, companyId);
        Invoice invoice = find(companyId, invoiceId);
        if (invoice.getSource() == InvoiceSource.XML) {
            throw new InvalidInvoiceException("Os valores de uma nota lida do XML não podem ser alterados: eles vêm "
                    + "do documento assinado. Só o status muda.");
        }
        InvoiceFields fields = data.toFields();
        checkTypedRules(company, fields);
        if (fields.accessKey() != null) {
            repository.findByCompanyIdAndAccessKey(companyId, fields.accessKey())
                    .filter(other -> !other.getId().equals(invoiceId))
                    .ifPresent(other -> {
                        throw new DuplicateInvoiceException(duplicateMessage(other));
                    });
        }
        invoice.correct(fields);
        return invoice;
    }

    @Transactional
    public Invoice cancel(Long userId, Long companyId, Long invoiceId) {
        companyService.getActiveOwned(userId, companyId);
        Invoice invoice = find(companyId, invoiceId);
        invoice.cancel();
        return invoice;
    }

    @Transactional
    public Invoice reactivate(Long userId, Long companyId, Long invoiceId) {
        companyService.getActiveOwned(userId, companyId);
        Invoice invoice = find(companyId, invoiceId);
        invoice.reactivate();
        return invoice;
    }

    /** Apaga a nota e o arquivo dela, liberando a cota. */
    @Transactional
    public void delete(Long userId, Long companyId, Long invoiceId) {
        companyService.getActiveOwned(userId, companyId);
        Invoice invoice = find(companyId, invoiceId);
        repository.delete(invoice);
        repository.flush();
        attachmentService.delete(userId, invoice.getAttachmentId());
    }

    /** O arquivo original, para o download. Arquivada pode baixar: é leitura. */
    @Transactional(readOnly = true)
    public Attachment file(Long userId, Long companyId, Long invoiceId) {
        companyService.getOwned(userId, companyId);
        return attachmentService.read(userId, find(companyId, invoiceId).getAttachmentId());
    }

    @Transactional(readOnly = true)
    public List<InvoiceExport> listAll(Long userId) {
        return repository.findAllOfUser(userId).stream().map(InvoiceExport::from).toList();
    }

    /** Para a exclusão da conta, antes dos anexos (as notas apontam para eles) e das empresas. */
    @Transactional
    public void deleteAllOf(Long userId) {
        repository.deleteAllByUserId(userId);
    }

    private Invoice find(Long companyId, Long invoiceId) {
        return repository.findByIdAndCompanyId(invoiceId, companyId).orElseThrow(InvoiceNotFoundException::new);
    }

    /** Regras de uma nota lida do XML, que traz o ambiente e o CNPJ do prestador. */
    private void checkXmlRules(Company company, ParsedInvoice parsed) {
        if (parsed.homologation()) {
            throw new InvalidInvoiceException("Esta é uma nota de homologação (ambiente de testes), sem valor fiscal. "
                    + "Envie a nota emitida em produção.");
        }
        if (company.getCnpj() != null && !company.getCnpj().equals(parsed.providerDocument())) {
            throw new InvalidInvoiceException("Esta nota foi emitida pelo CNPJ " + document(parsed.providerDocument())
                    + ", que não é o desta empresa (" + document(company.getCnpj()) + ").");
        }
        checkCompetence(company, parsed.competenceDate());
    }

    /** Regras dos campos digitados ou conferidos (PDF e manual). */
    private void checkTypedRules(Company company, InvoiceFields fields) {
        checkCompetence(company, fields.competenceDate());
        if (fields.issueDate().isAfter(LocalDate.now(clock))) {
            throw new InvalidInvoiceException("A data de emissão não pode estar no futuro.");
        }
        if (fields.unconditionalDiscountAmount() != null
                && fields.unconditionalDiscountAmount().compareTo(fields.serviceAmount()) > 0) {
            throw new InvalidInvoiceException("O desconto não pode ser maior que o valor do serviço.");
        }
    }

    private static void checkCompetence(Company company, LocalDate competence) {
        if (competence.isBefore(company.getOpeningDate())) {
            throw new InvalidInvoiceException("A competência desta nota (" + BR_DATE.format(competence)
                    + ") é anterior à abertura da empresa (" + BR_DATE.format(company.getOpeningDate()) + ").");
        }
    }

    private List<String> warnings(Company company, ParsedInvoice parsed) {
        List<String> warnings = new ArrayList<>();
        if (company.getCnpj() == null && parsed.providerDocument() != null) {
            warnings.add("A empresa não tem CNPJ cadastrado. Esta nota foi emitida pelo CNPJ "
                    + document(parsed.providerDocument()) + ".");
        }
        if (parsed.replacedAccessKey() != null) {
            repository.findByCompanyIdAndAccessKey(company.getId(), parsed.replacedAccessKey()).ifPresent(replaced ->
                    warnings.add("Esta nota substitui a nº " + replaced.getNumber() + " (competência "
                            + BR_DATE.format(replaced.getCompetenceDate()) + "), que será marcada como cancelada."));
        }
        return warnings;
    }

    private static String duplicateMessage(Invoice existing) {
        return "Você já cadastrou esta nota (nº " + existing.getNumber() + ", competência "
                + BR_DATE.format(existing.getCompetenceDate()) + ").";
    }

    /** O CNPJ formatado quando é um CNPJ; um CPF ou outro documento vai como veio. */
    private static String document(String value) {
        if (value == null) {
            return "não informado";
        }
        try {
            return Cnpj.parse(value).formatted();
        } catch (InvalidCompanyDataException e) {
            return value;
        }
    }
}
