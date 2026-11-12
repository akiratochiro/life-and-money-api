package com.akiratochiro.life_and_money_api.support;

public class SupportUnavailableException extends RuntimeException {
    SupportUnavailableException(Throwable cause) {
        super("Não foi possível enviar a mensagem agora. Tente de novo em alguns minutos.", cause);
    }
}
