package com.akiratochiro.life_and_money_api.company;

import com.akiratochiro.life_and_money_api.ratelimit.RateLimit;
import com.akiratochiro.life_and_money_api.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Pré-preenchimento do cadastro pelo CNPJ. Nada aqui pode impedir o cadastro: se a BrasilAPI
 * falhar, a resposta diz {@code available=false} e o formulário segue à mão.
 */
@Service
public class CnpjService {

    private static final Logger log = LoggerFactory.getLogger(CnpjService.class);

    private final CnpjLookup lookup;
    private final RateLimiter rateLimiter;

    public CnpjService(CnpjLookup lookup, RateLimiter rateLimiter) {
        this.lookup = lookup;
        this.rateLimiter = rateLimiter;
    }

    /** O limite é por usuário: protege a BrasilAPI (e a nossa instância) de um formulário em loop. */
    public CnpjLookupResponse lookup(Long userId, String rawCnpj) {
        Cnpj cnpj = Cnpj.parse(rawCnpj);
        rateLimiter.consume(RateLimit.CNPJ_LOOKUP_PER_USER, String.valueOf(userId));
        try {
            return lookup.find(cnpj).map(CnpjLookupResponse::of).orElseGet(CnpjLookupResponse::notFound);
        } catch (CnpjLookupException e) {
            log.warn("Consulta de CNPJ indisponível: {}", e.getMessage());
            return CnpjLookupResponse.unavailable();
        }
    }
}
