package com.akiratochiro.life_and_money_api.taxparam;

import com.akiratochiro.life_and_money_api.taxengine.Annex;
import com.akiratochiro.life_and_money_api.taxengine.SimplesBracket;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "simples_brackets")
class SimplesBracketRow {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private Annex annex;

    private int bracket;
    private LocalDate validFrom;
    private BigDecimal revenueUpTo;
    private BigDecimal nominalRate;
    private BigDecimal deduction;
    private BigDecimal irpjShare;
    private BigDecimal csllShare;
    private BigDecimal cofinsShare;
    private BigDecimal pisShare;
    private BigDecimal cppShare;
    private BigDecimal issShare;
    private String source;

    protected SimplesBracketRow() {
    }

    LocalDate getValidFrom() { return validFrom; }
    String getSource() { return source; }

    SimplesBracket toBracket() {
        return new SimplesBracket(annex, bracket, revenueUpTo, nominalRate, deduction, irpjShare, csllShare, cofinsShare,
                pisShare, cppShare, issShare);
    }
}
