package com.akiratochiro.life_and_money_api.email;

/**
 * Um e-mail pronto para sair. O texto puro acompanha o HTML porque alguns clientes (e filtros de
 * spam) leem só ele.
 */
public record EmailMessage(String to, String subject, String html, String text, String replyTo) {

    /** Sem Reply-To: a resposta vai para o remetente, como em todo e-mail da conta. */
    public EmailMessage(String to, String subject, String html, String text) {
        this(to, subject, html, text, null);
    }
}
