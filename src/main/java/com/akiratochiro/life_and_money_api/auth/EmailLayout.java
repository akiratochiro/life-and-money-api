package com.akiratochiro.life_and_money_api.auth;

import org.springframework.web.util.HtmlUtils;

/**
 * O visual dos e-mails do app, num lugar só. Estilos inline e layout em tabela porque é o que os
 * clientes de e-mail respeitam: CSS externo e flexbox são ignorados em boa parte deles. As cores
 * são as do app: fundo azul-tinta e o laranja como único acento.
 *
 * <p><b>Tudo o que entra aqui é escapado</b>: o nome vem do cadastro, digitado pelo usuário, e não
 * pode virar HTML.
 */
final class EmailLayout {

    private EmailLayout() {
    }

    static String html(String heading, String body, String buttonText, String link, String footer) {
        return """
                <!doctype html>
                <html lang="pt-BR">
                <body style="margin:0;padding:0;background:#070b17;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#070b17;padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#0b1020;border-radius:28px;padding:40px 32px;font-family:Helvetica,Arial,sans-serif;color:#f2f4fa;">
                        <tr><td style="font-size:12px;font-weight:700;letter-spacing:3px;text-transform:uppercase;color:#ff7a1f;">Life and Money</td></tr>
                        <tr><td style="padding-top:16px;font-size:24px;font-weight:700;line-height:1.3;">%s</td></tr>
                        <tr><td style="padding-top:16px;font-size:15px;line-height:1.6;color:#c9d0e6;">%s</td></tr>
                        <tr><td style="padding-top:32px;">
                          <a href="%s" style="display:inline-block;background:#ff7a1f;color:#0b1020;font-weight:700;font-size:16px;text-decoration:none;padding:16px 28px;border-radius:999px;">%s</a>
                        </td></tr>
                        <tr><td style="padding-top:32px;font-size:13px;line-height:1.6;color:#a7b0cc;">%s</td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(escape(heading), escape(body), escape(link), escape(buttonText), escape(footer));
    }

    static String firstName(String name) {
        String trimmed = name == null ? "" : name.strip();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
