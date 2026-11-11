package com.akiratochiro.life_and_money_api;

import com.akiratochiro.life_and_money_api.company.Cnpj;
import com.akiratochiro.life_and_money_api.company.CnpjData;
import com.akiratochiro.life_and_money_api.company.CnpjLookup;
import com.akiratochiro.life_and_money_api.company.CnpjLookupException;
import com.akiratochiro.life_and_money_api.company.LegalNature;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * CNPJs dos testes de integração, sem rede. Um teste pode simular a BrasilAPI fora do ar com
 * {@link #failing(boolean)}.
 */
public class FixedCnpjLookup implements CnpjLookup {

    public static final String KNOWN_MEI = "11222333000181";

    private static final Map<String, CnpjData> KNOWN = Map.of(
            KNOWN_MEI, new CnpjData("ANA SOUZA 12345678900", "Ana Design", LocalDate.of(2024, 3, 10), LegalNature.MEI));

    private volatile boolean failing;

    public void failing(boolean failing) {
        this.failing = failing;
    }

    @Override
    public Optional<CnpjData> find(Cnpj cnpj) {
        if (failing) {
            throw new CnpjLookupException("BrasilAPI simulada fora do ar", null);
        }
        return Optional.ofNullable(KNOWN.get(cnpj.value()));
    }
}
