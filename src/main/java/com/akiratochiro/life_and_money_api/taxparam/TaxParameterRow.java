package com.akiratochiro.life_and_money_api.taxparam;

import com.akiratochiro.life_and_money_api.taxengine.TaxParameter;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Uma linha de {@code tax_parameters}: só leitura, escrita pelas migrations. */
@Entity
@Table(name = "tax_parameters")
class TaxParameterRow {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private TaxParameter key;

    private LocalDate validFrom;
    private BigDecimal value;
    private String source;

    protected TaxParameterRow() {
    }

    TaxParameter getKey() { return key; }
    LocalDate getValidFrom() { return validFrom; }
    BigDecimal getValue() { return value; }
    String getSource() { return source; }
}
