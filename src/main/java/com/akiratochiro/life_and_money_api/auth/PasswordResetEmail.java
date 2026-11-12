package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.email.PlainEmail;
import com.akiratochiro.life_and_money_api.email.EmailMessage;

/** O e-mail com o link para criar uma senha nova. */
final class PasswordResetEmail {

    static final String SUBJECT = "Crie uma senha nova no Life and Money";

    private PasswordResetEmail() {
    }

    static EmailMessage compose(String to, String name, String link) {
        return PlainEmail.to(to, SUBJECT)
                .paragraph(PlainEmail.greeting(name))
                .paragraph("Recebemos um pedido para redefinir a senha da sua conta no Life and Money. Para criar "
                        + "uma senha nova, abra o link abaixo. Ele vale por " + PasswordResetService.VALIDITY.toMinutes()
                        + " minutos e só pode ser usado uma vez.")
                .link(link)
                .paragraph("Se você não pediu isso, ignore este e-mail: sua senha continua a mesma.")
                .build();
    }
}
