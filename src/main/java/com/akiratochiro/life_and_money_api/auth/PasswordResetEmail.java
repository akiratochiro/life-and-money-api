package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.email.EmailMessage;

/** O e-mail com o link para criar uma senha nova. O visual vem do {@link EmailLayout}. */
final class PasswordResetEmail {

    static final String SUBJECT = "Crie uma senha nova no Life and Money";

    private PasswordResetEmail() {
    }

    static EmailMessage compose(String to, String name, String link) {
        long minutes = PasswordResetService.VALIDITY.toMinutes();
        String heading = "Vamos criar uma senha nova, " + EmailLayout.firstName(name) + "?";
        String body = "Recebemos um pedido para redefinir a senha da sua conta. O link abaixo vale por "
                + minutes + " minutos e só pode ser usado uma vez.";
        String footer = "Se você não pediu isso, ignore este e-mail: sua senha continua a mesma.";

        String html = EmailLayout.html(heading, body, "Criar senha nova", link, footer);
        String text = """
                %s

                Recebemos um pedido para redefinir a senha da sua conta no Life and Money.
                Abra o link abaixo em até %d minutos (ele só pode ser usado uma vez):

                %s

                %s
                """.formatted(heading, minutes, link, footer);

        return new EmailMessage(to, SUBJECT, html, text);
    }
}
