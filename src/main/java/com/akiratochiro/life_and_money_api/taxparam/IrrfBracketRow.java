package com.akiratochiro.life_and_money_api.taxparam;

import com.akiratochiro.life_and_money_api.taxengine.IrrfBracket;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "irrf_brackets")
class IrrfBracketRow {

    @Id
    private Long id;

    private LocalDate validFrom;
    private int bracket;
    private BigDecimal incomeUpTo;
    private BigDecimal rate;
    private BigDecimal deduction;
    private String source;

    protected IrrfBracketRow() {
    }

    LocalDate getValidFrom() { return validFrom; }
    String getSource() { return source; }

    IrrfBracket toBracket() {
        return new IrrfBracket(bracket, incomeUpTo, rate, deduction);
    }
}
