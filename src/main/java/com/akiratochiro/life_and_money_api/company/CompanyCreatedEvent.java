package com.akiratochiro.life_and_money_api.company;

/**
 * Uma empresa acabou de ser cadastrada. Quem depende de {@code company} e precisa preparar algo
 * para ela (as categorias de despesa, por exemplo) escuta este evento: o {@code company} não pode
 * chamar essas features, porque a dependência vai no outro sentido.
 */
public record CompanyCreatedEvent(Long userId, Long companyId) {
}
