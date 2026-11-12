package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.email.PlainEmail;
import com.akiratochiro.life_and_money_api.email.EmailMessage;

/** Os dois e-mails de um pedido de cadastro. */
final class RegistrationEmails {

    static final String CONFIRMATION_SUBJECT = "Confirme seu e-mail no Life and Money";
    static final String ALREADY_REGISTERED_SUBJECT = "Você já tem uma conta no Life and Money";

    private RegistrationEmails() {
    }

    /** O link que cria a conta. */
    static EmailMessage confirmation(String to, String name, String link) {
        return PlainEmail.to(to, CONFIRMATION_SUBJECT)
                .paragraph(PlainEmail.greeting(name))
                .paragraph("Para ativar sua conta no Life and Money, confirme que este e-mail é seu abrindo o link "
                        + "abaixo. Ele vale por " + RegistrationService.VALIDITY.toHours()
                        + " horas e só pode ser usado uma vez.")
                .link(link)
                .paragraph("Se você não criou essa conta, ignore este e-mail: nenhuma conta é criada sem essa "
                        + "confirmação.")
                .paragraph("Se este e-mail foi parar no spam, marque como “não é spam” para os próximos chegarem "
                        + "na caixa de entrada.")
                .build();
    }

    /**
     * Para quem já tem conta, no lugar do link: alguém (talvez a própria pessoa, que esqueceu) tentou
     * se cadastrar com este e-mail. Sem saudação pelo nome: o nome digitado no cadastro pode ser de
     * qualquer um.
     */
    static EmailMessage alreadyRegistered(String to, String forgotPasswordLink) {
        return PlainEmail.to(to, ALREADY_REGISTERED_SUBJECT)
                .paragraph("Olá.")
                .paragraph("Alguém tentou criar uma conta no Life and Money com este e-mail, mas ele já está "
                        + "cadastrado. Se foi você, é só entrar com a sua senha. Se não lembra a senha, crie uma "
                        + "nova por este link:")
                .link(forgotPasswordLink)
                .paragraph("Se não foi você, ignore este e-mail: nada mudou na sua conta, e a senha continua a mesma.")
                .build();
    }
}
