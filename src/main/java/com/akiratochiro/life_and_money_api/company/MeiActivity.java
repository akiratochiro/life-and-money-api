package com.akiratochiro.life_and_money_api.company;

/**
 * Atividade do MEI, que define o valor do DAS-MEI: o INSS é igual para todos, e somam-se o ICMS
 * (comércio ou indústria), o ISS (serviços) ou os dois.
 */
public enum MeiActivity {
    SERVICOS,
    COMERCIO_INDUSTRIA,
    AMBOS
}
