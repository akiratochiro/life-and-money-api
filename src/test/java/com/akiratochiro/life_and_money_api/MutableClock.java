package com.akiratochiro.life_and_money_api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Relógio dos testes de integração. Anda junto com o relógio do sistema até um teste pedir para
 * avançar; a partir daí fica parado no instante pedido, até o {@link #reset()}.
 */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant fixed;

    public MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    public void advance(Duration amount) {
        fixed = instant().plus(amount);
    }

    public void reset() {
        fixed = null;
    }

    @Override
    public Instant instant() {
        Instant current = fixed;
        return current != null ? current : Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId otherZone) {
        return Clock.fixed(instant(), otherZone);
    }
}
