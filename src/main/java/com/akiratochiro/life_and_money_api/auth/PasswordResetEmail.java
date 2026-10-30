package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.email.EmailMessage;
import org.springframework.web.util.HtmlUtils;

/**
 * O e-mail com o link. Estilos inline e layout em tabela porque é o que os clientes de e-mail
 * respeitam: CSS externo e flexbox são ignorados em boa parte deles. As cores são as do app —
 * fundo azul-tinta e o laranja como único acento.
 */
final class PasswordResetEmail {

    static final String SUBJECT = "Crie uma senha nova no Life and Money";

    private PasswordResetEmail() {
    }

    static EmailMessage compose(String to, String name, String link) {
        long minutes = PasswordResetService.VALIDITY.toMinutes();
        // O nome vem do cadastro, digitado pelo usuário: escapado, não vira HTML.
        String safeName = HtmlUtils.htmlEscape(firstName(name));
        String safeLink = HtmlUtils.htmlEscape(link);

        String html = """
                <!doctype html>
                <html lang="pt-BR">
                <body style="margin:0;padding:0;background:#070b17;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#070b17;padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#0b1020;border-radius:28px;padding:40px 32px;font-family:Helvetica,Arial,sans-serif;color:#f2f4fa;">
                        <tr><td style="font-size:12px;font-weight:700;letter-spacing:3px;text-transform:uppercase;color:#ff7a1f;">Life and Money</td></tr>
                        <tr><td style="padding-top:16px;font-size:24px;font-weight:700;line-height:1.3;">Vamos criar uma senha nova, %s?</td></tr>
                        <tr><td style="padding-top:16px;font-size:15px;line-height:1.6;color:#c9d0e6;">Recebemos um pedido para redefinir a senha da sua conta. O link abaixo vale por %d minutos e só pode ser usado uma vez.</td></tr>
                        <tr><td style="padding-top:32px;">
                          <a href="%s" style="display:inline-block;background:#ff7a1f;color:#0b1020;font-weight:700;font-size:16px;text-decoration:none;padding:16px 28px;border-radius:999px;">Criar senha nova</a>
                        </td></tr>
                        <tr><td style="padding-top:32px;font-size:13px;line-height:1.6;color:#a7b0cc;">Se você não pediu isso, ignore este e-mail: sua senha continua a mesma.</td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(safeName, minutes, safeLink);

        String text = """
                Vamos criar uma senha nova, %s?

                Recebemos um pedido para redefinir a senha da sua conta no Life and Money.
                Abra o link abaixo em até %d minutos (ele só pode ser usado uma vez):

                %s

                Se você não pediu isso, ignore este e-mail: sua senha continua a mesma.
                """.formatted(firstName(name), minutes, link);

        return new EmailMessage(to, SUBJECT, html, text);
    }

    private static String firstName(String name) {
        String trimmed = name == null ? "" : name.strip();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }
}
