package com.akiratochiro.life_and_money_api.ratelimit;

import java.time.Duration;

public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    RateLimitExceededException(Duration retryAfter) {
        super("Limite de tentativas atingido");
        this.retryAfter = retryAfter;
    }

    /** Quanto falta para a próxima tentativa ser aceita. */
    public Duration getRetryAfter() {
        return retryAfter;
    }
}
