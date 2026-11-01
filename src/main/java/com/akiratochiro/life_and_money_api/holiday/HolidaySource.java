package com.akiratochiro.life_and_money_api.holiday;

import java.util.List;

/**
 * De onde vêm os feriados nacionais de um ano. Em produção, a BrasilAPI; nos testes de
 * integração, uma lista fixa, para que nenhum teste dependa de um serviço externo no ar.
 */
public interface HolidaySource {

    /** @throws HolidaySourceException se a fonte não respondeu, ou respondeu algo inválido */
    List<Holiday> fetch(int year);
}
