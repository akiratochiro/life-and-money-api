package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.email.EmailMessage;

/** Os dois e-mails de um pedido de cadastro. O visual vem do {@link EmailLayout}. */
final class RegistrationEmails {

    static final String CONFIRMATION_SUBJECT = "Confirme seu e-mail no Life and Money";
    static final String ALREADY_REGISTERED_SUBJECT = "Você já tem uma conta no Life and Money";

    private RegistrationEmails() {
    }

    /** O link que cria a conta. */
    static EmailMessage confirmation(String to, String name, String link) {
        long hours = RegistrationService.VALIDITY.toHours();
        String heading = "Falta um passo, " + EmailLayout.firstName(name) + "!";
        String body = "Confirme que este e-mail é seu para ativar a conta. O link vale por " + hours
                + " horas e só pode ser usado uma vez.";
        String footer = "Se você não criou uma conta no Life and Money, ignore este e-mail: nenhuma conta "
                + "será criada sem esta confirmação.";

        String text = """
                %s

                Confirme que este e-mail é seu para ativar a conta no Life and Money.
                Abra o link abaixo em até %d horas (ele só pode ser usado uma vez):

                %s

                %s
                """.formatted(heading, hours, link, footer);
        return new EmailMessage(to, CONFIRMATION_SUBJECT,
                EmailLayout.html(heading, body, "Confirmar meu e-mail", link, footer), text);
    }

    /**
     * Para quem já tem conta, no lugar do link: alguém (talvez a própria pessoa, que esqueceu) tentou
     * se cadastrar com este e-mail. O nome é o da conta, não o digitado no cadastro, que pode ser de
     * qualquer um.
     */
    static EmailMessage alreadyRegistered(String to, String forgotPasswordLink) {
        String heading = "Você já tem uma conta";
        String body = "Alguém tentou criar uma conta no Life and Money com este e-mail, mas ele já está "
                + "cadastrado. Se foi você, é só entrar; se não lembra a senha, crie uma nova pelo link abaixo.";
        String footer = "Se não foi você, ignore este e-mail: nada mudou na sua conta, e a senha continua a mesma.";

        String text = """
                %s

                %s

                %s

                %s
                """.formatted(heading, body, forgotPasswordLink, footer);
        return new EmailMessage(to, ALREADY_REGISTERED_SUBJECT,
                EmailLayout.html(heading, body, "Esqueci minha senha", forgotPasswordLink, footer), text);
    }
}
