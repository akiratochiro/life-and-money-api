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
 *
 * <p>Só o XML entra, por enquanto (atualização da decisão 20): os valores vêm do documento assinado e
 * ficam travados. Um PDF é recusado com a orientação de baixar o XML.
 */
@Service
public class InvoiceService {

    /** O que é guardado: só o XML. O PDF é reconhecido apenas para a recusa explicar o que fazer. */
    static final Set<FileType> ACCEPTED = Set.of(FileType.XML);
    private static final Set<FileType> RECOGNIZED = Set.of(FileType.XML, FileType.PDF);
    static final String XML_ONLY = "Por enquanto, as notas entram só pelo XML. Baixe o XML da nota no portal da NFS-e "
            + "(o mesmo lugar de onde vem o PDF) e envie esse arquivo.";
    static final int MAX_FILES = 10;
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InvoiceRepository repository;
    private final CompanyService companyService;
    private final AttachmentService attachmentService;

    public InvoiceService(InvoiceRepository repository, CompanyService companyService,
                          AttachmentService attachmentService) {
        this.repository = repository;
        this.companyService = companyService;
        this.attachmentService = attachmentService;
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
            type = AttachmentService.check(file.filename(), file.content(), RECOGNIZED);
        } catch (InvalidAttachmentException e) {
            return InvoicePreview.rejected(file.filename(), null, e.getMessage());
        }
        if (type == FileType.PDF) {
            return InvoicePreview.rejected(file.filename(), type, XML_ONLY);
        }

        ParsedInvoice parsed;
        try {
            parsed = NfseXmlParser.parse(file.content());
            checkRules(company, parsed);
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

    /**
     * Salva uma nota a partir do XML: o servidor relê o arquivo e usa só os valores dele, que não
     * mudam depois (só o status muda).
     */
    @Transactional
    public Invoice save(Long userId, Long companyId, Upload file) {
        Company company = companyService.getActiveOwned(userId, companyId);
        if (AttachmentService.check(file.filename(), file.content(), RECOGNIZED) == FileType.PDF) {
            throw new InvalidInvoiceException(XML_ONLY);
        }
        ParsedInvoice parsed = NfseXmlParser.parse(file.content());
        checkRules(company, parsed);
        InvoiceFields fields = InvoiceFields.from(parsed);

        repository.findByCompanyIdAndAccessKey(companyId, fields.accessKey()).ifPresent(existing -> {
            throw new DuplicateInvoiceException(duplicateMessage(existing));
        });

        Attachment attachment = attachmentService.store(userId, file.filename(), file.content(), ACCEPTED);
        Invoice invoice;
        try {
            // saveAndFlush: o UNIQUE do banco barra a mesma nota enviada em dois cliques simultâneos,
            // e o erro precisa aparecer aqui para virar a mesma mensagem amigável.
            invoice = repository.saveAndFlush(new Invoice(userId, companyId, attachment.getId(), InvoiceSource.XML,
                    fields, parsed.replacedAccessKey()));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateInvoiceException("Você já cadastrou esta nota.");
        }

        // A substituta cancela a substituída: legalmente, a anterior foi cancelada pela substituição.
        if (parsed.replacedAccessKey() != null) {
            repository.findByCompanyIdAndAccessKey(companyId, parsed.replacedAccessKey()).ifPresent(Invoice::cancel);
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
    private void checkRules(Company company, ParsedInvoice parsed) {
        if (parsed.homologation()) {
            throw new InvalidInvoiceException("Esta é uma nota de homologação (ambiente de testes), sem valor fiscal. "
                    + "Envie a nota emitida em produção.");
        }
        if (company.getCnpj() != null && !company.getCnpj().equals(parsed.providerDocument())) {
            throw new InvalidInvoiceException("Esta nota foi emitida pelo CNPJ " + document(parsed.providerDocument())
                    + ", que não é o desta empresa (" + document(company.getCnpj()) + ").");
        }
        if (parsed.competenceDate().isBefore(company.getOpeningDate())) {
            throw new InvalidInvoiceException("A competência desta nota (" + BR_DATE.format(parsed.competenceDate())
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
