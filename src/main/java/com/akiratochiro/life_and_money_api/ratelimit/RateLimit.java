package com.akiratochiro.life_and_money_api.ratelimit;

import java.time.Duration;

/**
 * Os limites de tentativas. A capacidade é quantas tentativas cabem de uma vez; elas voltam aos
 * poucos ao longo do período (o de cadastro devolve uma a cada 12 minutos), em vez de todas de uma
 * vez ao fim dele.
 */
public enum RateLimit {

    /** Folgado de propósito: várias pessoas podem sair pelo mesmo IP (casa, escritório, 4G). */
    LOGIN_PER_IP(20, Duration.ofMinutes(1)),

    /** Segura o teste de senhas contra uma conta, mesmo que venha de muitos IPs. */
    LOGIN_PER_EMAIL(10, Duration.ofMinutes(15)),

    REGISTER_PER_IP(5, Duration.ofHours(1)),

    /** Soma-se ao intervalo de 1 minuto por conta do próprio fluxo de redefinição. */
    FORGOT_PASSWORD_PER_IP(5, Duration.ofMinutes(15)),

    /** O app renova a cada ~14 minutos; várias abas e várias pessoas no mesmo IP somam. */
    REFRESH_PER_IP(30, Duration.ofMinutes(1)),

    /**
     * Tentativas de senha para excluir a conta. Por usuário: quem tem um access token roubado não
     * pode usar este endpoint para testar senhas.
     */
    ACCOUNT_DELETION_PER_USER(5, Duration.ofMinutes(15));

    final long capacity;
    final Duration period;

    RateLimit(long capacity, Duration period) {
        this.capacity = capacity;
        this.period = period;
    }
}
