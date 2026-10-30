package com.akiratochiro.life_and_money_api.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Limites de tentativas em memória, um balde (token bucket) por limite e por chave.
 *
 * <p>Em memória porque há uma única instância da API: os baldes valem para todo o tráfego. O
 * preço é que eles zeram a cada deploy ou reinício, e que, com duas instâncias, cada uma teria os
 * seus — o limite efetivo dobraria. A partir daí seria preciso guardá-los fora (Redis, por exemplo).
 *
 * <p>Os baldes ficam num cache com teto de tamanho, e não num mapa simples: quem varia o IP ou o
 * e-mail a cada requisição criaria baldes sem fim, e a memória da instância gratuita acabaria
 * antes do ataque. Um balde parado some depois de duas horas, mais do que o maior período.
 */
@Component
public class RateLimiter {

    private static final long MAX_BUCKETS = 100_000;
    private static final Duration IDLE_EXPIRATION = Duration.ofHours(2);

    private final boolean enabled;
    private final TimeMeter timeMeter;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAX_BUCKETS)
            .expireAfterAccess(IDLE_EXPIRATION)
            .build();

    public RateLimiter(@Value("${app.rate-limit.enabled:true}") boolean enabled, Clock clock) {
        this.enabled = enabled;
        this.timeMeter = new ClockTimeMeter(clock);
    }

    /**
     * Gasta uma tentativa do balde. O e-mail é normalizado como no cadastro, para "Ana@x" e
     * "ana@x " contarem juntos.
     *
     * @throws RateLimitExceededException se o balde está vazio, com o tempo até a próxima tentativa
     */
    public void consume(RateLimit limit, String key) {
        if (!enabled) {
            return;
        }
        String normalized = key == null ? "" : key.strip().toLowerCase(Locale.ROOT);
        Bucket bucket = buckets.get(limit.name() + ":" + normalized, ignored -> newBucket(limit));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new RateLimitExceededException(Duration.ofNanos(probe.getNanosToWaitForRefill()));
        }
    }

    private Bucket newBucket(RateLimit limit) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit.capacity)
                        .refillGreedy(limit.capacity, limit.period)
                        .build())
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    /** O tempo do Bucket4j vem do mesmo Clock do resto da aplicação, que os testes controlam. */
    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            Instant now = clock.instant();
            return now.getEpochSecond() * 1_000_000_000L + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
