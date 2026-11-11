package com.akiratochiro.life_and_money_api.company;

import com.akiratochiro.life_and_money_api.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A empresa e o enquadramento dela. É o portão das features da Minha Empresa: toda operação numa
 * empresa passa por {@link #getOwned} ou {@link #getActiveOwned}, que respondem 404 para empresa
 * de outro usuário e 409 para escrita em empresa arquivada.
 */
@Service
public class CompanyService {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CompanyRepository companyRepository;
    private final CompanyRegimeRepository regimeRepository;
    private final PartnerRepository partnerRepository;
    private final UserService userService;
    private final List<OpeningDateLock> openingDateLocks;
    private final Clock clock;

    public CompanyService(CompanyRepository companyRepository,
                          CompanyRegimeRepository regimeRepository,
                          PartnerRepository partnerRepository,
                          UserService userService,
                          List<OpeningDateLock> openingDateLocks,
                          Clock clock) {
        this.companyRepository = companyRepository;
        this.regimeRepository = regimeRepository;
        this.partnerRepository = partnerRepository;
        this.userService = userService;
        this.openingDateLocks = openingDateLocks;
        this.clock = clock;
    }

    /**
     * Cria a empresa, a primeira vigência do enquadramento (a partir do mês da abertura) e o sócio
     * "sou eu", numa transação só: uma empresa sem enquadramento ou sem sócio não existe.
     */
    @Transactional
    public CompanyDetails create(Long userId, NewCompany data) {
        String name = requireName(data.name());
        String emoji = CompanyEmoji.validate(data.emoji());
        Cnpj cnpj = optionalCnpj(data.cnpj());
        LocalDate today = LocalDate.now(clock);

        LocalDate openingDate = data.openingDate();
        requireNotFuture(openingDate, today, "openingDate", "A data de abertura não pode estar no futuro.");
        LocalDate balanceDate = data.openingBalanceDate() != null ? data.openingBalanceDate() : today;
        validateBalanceDate(balanceDate, openingDate, today, "openingBalanceDate");

        RegimeRules.validate(data.legalNature(), data.taxRegime(), data.annexConfig(), data.meiActivity());

        if (cnpj != null && companyRepository.existsByUserIdAndCnpj(userId, cnpj.value())) {
            throw new CnpjAlreadyRegisteredException();
        }

        Company company = companyRepository.save(new Company(
                userId, name, emoji, cnpj, openingDate,
                data.fatorRTarget() != null ? data.fatorRTarget() : Company.DEFAULT_FATOR_R_TARGET,
                data.openingBalance() != null ? data.openingBalance() : BigDecimal.ZERO,
                balanceDate));

        CompanyRegime regime = regimeRepository.save(new CompanyRegime(
                userId, company.getId(), data.legalNature(), data.taxRegime(),
                data.annexConfig(), data.meiActivity(), YearMonth.from(openingDate)));

        String partnerName = data.partnerName() != null && !data.partnerName().isBlank()
                ? data.partnerName().strip()
                : userService.findById(userId).getName();
        partnerRepository.save(new Partner(userId, company.getId(), partnerName, true));

        return new CompanyDetails(company, regime, true);
    }

    /** Em ordem alfabética do português, como as contas ("Água" antes de "internet"). */
    @Transactional(readOnly = true)
    public List<CompanyDetails> list(Long userId, boolean includeArchived) {
        List<Company> companies = includeArchived
                ? companyRepository.findAllByUserId(userId)
                : companyRepository.findAllByUserIdAndArchivedFalse(userId);
        return companies.stream()
                .sorted(Comparator.comparing(Company::getName, byName()))
                .map(this::details)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyDetails get(Long userId, Long companyId) {
        return details(getOwned(userId, companyId));
    }

    /**
     * Edição parcial. O enquadramento não muda por aqui: ele tem histórico, e uma mudança abre uma
     * vigência nova ({@code changeRegime}).
     */
    @Transactional
    public CompanyDetails update(Long userId, Long companyId, CompanyChanges changes) {
        Company company = getActiveOwned(userId, companyId);
        LocalDate today = LocalDate.now(clock);

        if (changes.name() != null) {
            company.rename(requireName(changes.name()));
        }
        if (changes.emoji() != null) {
            company.changeEmoji(CompanyEmoji.validate(changes.emoji()));
        }
        if (changes.cnpj() != null) {
            Cnpj cnpj = optionalCnpj(changes.cnpj());
            if (cnpj != null && companyRepository.existsByUserIdAndCnpjAndIdNot(userId, cnpj.value(), companyId)) {
                throw new CnpjAlreadyRegisteredException();
            }
            company.changeCnpj(cnpj);
        }
        if (changes.fatorRTarget() != null) {
            company.changeFatorRTarget(changes.fatorRTarget());
        }

        LocalDate openingDate = company.getOpeningDate();
        boolean openingDateChanges = changes.openingDate() != null && !changes.openingDate().equals(openingDate);
        if (openingDateChanges) {
            requireNotFuture(changes.openingDate(), today, "openingDate", "A data de abertura não pode estar no futuro.");
            if (!isOpeningDateEditable(companyId)) {
                throw new OpeningDateLockedException();
            }
            openingDate = changes.openingDate();
        }

        BigDecimal balance = changes.openingBalance() != null ? changes.openingBalance() : company.getOpeningBalance();
        LocalDate balanceDate = changes.openingBalanceDate() != null
                ? changes.openingBalanceDate()
                : company.getOpeningBalanceDate();
        // O campo marcado é o que o usuário mexeu: se mudou só a abertura, o erro aparece nela.
        String balanceField = changes.openingBalanceDate() == null && openingDateChanges
                ? "openingDate"
                : "openingBalanceDate";
        validateBalanceDate(balanceDate, openingDate, today, balanceField);

        if (openingDateChanges) {
            company.changeOpeningDate(openingDate);
            regimeRepository.findByCompanyIdAndValidToIsNull(companyId)
                    .orElseThrow()
                    .moveStartTo(YearMonth.from(openingDate));
        }
        company.changeOpeningBalance(balance, balanceDate);

        return details(company);
    }

    /** Arquivar de novo não muda nada, como nas categorias. */
    @Transactional
    public CompanyDetails archive(Long userId, Long companyId) {
        Company company = getOwned(userId, companyId);
        if (!company.isArchived()) {
            company.archive(clock.instant());
        }
        return details(company);
    }

    @Transactional
    public CompanyDetails unarchive(Long userId, Long companyId) {
        Company company = getOwned(userId, companyId);
        if (company.isArchived()) {
            company.unarchive();
        }
        return details(company);
    }

    /** Leitura: a empresa do usuário, arquivada ou não. */
    @Transactional(readOnly = true)
    public Company getOwned(Long userId, Long companyId) {
        return companyRepository.findByIdAndUserId(companyId, userId)
                .orElseThrow(CompanyNotFoundException::new);
    }

    /** Escrita: a empresa do usuário, que não pode estar arquivada. */
    @Transactional(readOnly = true)
    public Company getActiveOwned(Long userId, Long companyId) {
        Company company = getOwned(userId, companyId);
        if (company.isArchived()) {
            throw new CompanyArchivedException();
        }
        return company;
    }

    /** Para o export da conta: todas as empresas, arquivadas inclusive, com histórico e sócios. */
    @Transactional(readOnly = true)
    public List<CompanyExport> listAll(Long userId) {
        return companyRepository.findAllByUserId(userId).stream()
                .sorted(Comparator.comparing(Company::getId))
                .map(company -> CompanyExport.from(company,
                        regimeRepository.findAllByCompanyIdOrderByValidFromAsc(company.getId()),
                        partnerRepository.findAllByCompanyIdOrderByIdAsc(company.getId())))
                .toList();
    }

    /** Para a exclusão da conta, na ordem das chaves estrangeiras: sócios e vigências antes da empresa. */
    @Transactional
    public void deleteAllOf(Long userId) {
        partnerRepository.deleteAllByUserId(userId);
        regimeRepository.deleteAllByUserId(userId);
        companyRepository.deleteAllByUserId(userId);
    }

    private CompanyDetails details(Company company) {
        YearMonth currentMonth = YearMonth.now(clock);
        List<CompanyRegime> regimes = regimeRepository.findAllByCompanyIdOrderByValidFromAsc(company.getId());
        CompanyRegime current = regimes.stream()
                .filter(regime -> regime.isValidIn(currentMonth))
                .findFirst()
                .orElse(regimes.getFirst());
        boolean editable = regimes.size() == 1 && noLockOn(company.getId());
        return new CompanyDetails(company, current, editable);
    }

    /**
     * A data de abertura pode mudar enquanto nada depende dela: o enquadramento nunca mudou (a
     * primeira vigência é a única e começa na abertura) e nenhuma feature a trava.
     */
    private boolean isOpeningDateEditable(Long companyId) {
        return regimeRepository.countByCompanyId(companyId) == 1 && noLockOn(companyId);
    }

    private boolean noLockOn(Long companyId) {
        return openingDateLocks.stream().noneMatch(lock -> lock.locksOpeningDate(companyId));
    }

    private static String requireName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidCompanyDataException("name", "Informe o nome da empresa.");
        }
        String name = raw.strip();
        if (name.length() > 100) {
            throw new InvalidCompanyDataException("name", "O nome deve ter no máximo 100 caracteres.");
        }
        return name;
    }

    /** Texto vazio é "sem CNPJ": o campo é opcional. */
    private static Cnpj optionalCnpj(String raw) {
        return raw == null || raw.isBlank() ? null : Cnpj.parse(raw);
    }

    private static void requireNotFuture(LocalDate date, LocalDate today, String field, String message) {
        if (date.isAfter(today)) {
            throw new InvalidCompanyDataException(field, message);
        }
    }

    private static void validateBalanceDate(LocalDate balanceDate, LocalDate openingDate, LocalDate today,
                                            String field) {
        requireNotFuture(balanceDate, today, field, "A data do saldo inicial não pode estar no futuro.");
        if (balanceDate.isBefore(openingDate)) {
            throw new InvalidCompanyDataException(field,
                    "A data do saldo inicial (" + BR_DATE.format(balanceDate)
                            + ") não pode ser anterior à abertura da empresa (" + BR_DATE.format(openingDate) + ").");
        }
    }

    /** Ordem alfabética do português; uma instância por chamada, porque o Collator não é thread-safe. */
    private static Comparator<String> byName() {
        Collator collator = Collator.getInstance(PT_BR);
        collator.setStrength(Collator.SECONDARY);
        return collator::compare;
    }
}
