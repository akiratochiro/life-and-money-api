package com.akiratochiro.life_and_money_api.company;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * O enquadramento de uma empresa num período: de {@code validFrom} até {@code validTo}, ou sem fim
 * quando é o mais recente. Como o orçamento (decisão 2), uma mudança fecha esta vigência e abre
 * outra, e os meses passados continuam calculados pelo enquadramento da época.
 */
@Entity
@Table(name = "company_regimes")
public class CompanyRegime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long companyId;

    @Enumerated(EnumType.STRING)
    private LegalNature legalNature;

    @Enumerated(EnumType.STRING)
    private TaxRegime taxRegime;

    @Enumerated(EnumType.STRING)
    private AnnexConfig annexConfig;

    @Enumerated(EnumType.STRING)
    private MeiActivity meiActivity;

    private LocalDate validFrom;
    private LocalDate validTo;
    private Instant createdAt;

    protected CompanyRegime() {
    }

    /** As regras de combinação já foram conferidas por {@link RegimeRules} antes de chegar aqui. */
    CompanyRegime(Long userId, Long companyId, LegalNature legalNature, TaxRegime taxRegime,
                  AnnexConfig annexConfig, MeiActivity meiActivity, YearMonth validFrom) {
        this.userId = userId;
        this.companyId = companyId;
        this.legalNature = legalNature;
        this.taxRegime = taxRegime;
        this.annexConfig = annexConfig;
        this.meiActivity = meiActivity;
        this.validFrom = validFrom.atDay(1);
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public LegalNature getLegalNature() {
        return legalNature;
    }

    public TaxRegime getTaxRegime() {
        return taxRegime;
    }

    public AnnexConfig getAnnexConfig() {
        return annexConfig;
    }

    public MeiActivity getMeiActivity() {
        return meiActivity;
    }

    public YearMonth getValidFrom() {
        return YearMonth.from(validFrom);
    }

    public YearMonth getValidTo() {
        return validTo == null ? null : YearMonth.from(validTo);
    }

    public boolean isActive() {
        return validTo == null;
    }

    public boolean isValidIn(YearMonth month) {
        return !month.isBefore(getValidFrom()) && (validTo == null || !month.isAfter(getValidTo()));
    }

    /** Mesmo enquadramento? Usado para recusar uma "mudança" que não muda nada. */
    boolean sameAs(LegalNature nature, TaxRegime regime, AnnexConfig annex, MeiActivity activity) {
        return legalNature == nature && taxRegime == regime && annexConfig == annex && meiActivity == activity;
    }

    void close(YearMonth lastMonth) {
        if (!isActive()) {
            throw new IllegalStateException("Company regime " + id + " is already closed");
        }
        if (lastMonth.isBefore(getValidFrom())) {
            throw new IllegalArgumentException("Cannot close company regime before its start month " + getValidFrom());
        }
        this.validTo = lastMonth.atDay(1);
    }

    /** Desfazer a última mudança: a vigência anterior volta a não ter fim. */
    void reopen() {
        this.validTo = null;
    }

    /**
     * Só para a primeira vigência, quando a data de abertura muda enquanto nada depende dela.
     * Ela continua começando no mês da abertura.
     */
    void moveStartTo(YearMonth month) {
        if (validTo != null && month.isAfter(getValidTo())) {
            throw new IllegalArgumentException("Cannot start company regime after its end month " + getValidTo());
        }
        this.validFrom = month.atDay(1);
    }
}
