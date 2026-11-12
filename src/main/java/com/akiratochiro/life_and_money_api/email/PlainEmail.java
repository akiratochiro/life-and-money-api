package com.akiratochiro.life_and_money_api.email;

import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Monta os e-mails do app como um e-mail comum, escrito por uma pessoa: parágrafos, o link por
 * extenso e uma assinatura. Sem cores, imagens nem botões.
 *
 * <p>Por quê: é o que se espera de um e-mail de conta, e um e-mail com cara de peça de marketing
 * (layout em tabela, cores, botão) pesa contra nos filtros de spam, que já desconfiam do remetente.
 * O link aparece por extenso para a pessoa ver para onde ele leva antes de clicar.
 *
 * <p>A versão HTML e a de texto saem do mesmo conteúdo, então não têm como divergir. No HTML,
 * <b>tudo é escapado</b>: o nome vem do cadastro, digitado pelo usuário, e não pode virar HTML.
 */
public final class PlainEmail {

    public static final String SIGNATURE = "Life and Money";

    private final String to;
    private final String subject;
    private String replyTo;
    private final List<String> htmlParts = new ArrayList<>();
    private final List<String> textParts = new ArrayList<>();

    private PlainEmail(String to, String subject) {
        this.to = to;
        this.subject = subject;
    }

    public static PlainEmail to(String to, String subject) {
        return new PlainEmail(to, subject);
    }

    public PlainEmail paragraph(String text) {
        htmlParts.add("<p>" + escape(text) + "</p>");
        textParts.add(text);
        return this;
    }

    /**
     * Um texto de várias linhas digitado por alguém (a mensagem do suporte): cada bloco separado por
     * linha em branco vira um parágrafo, e as quebras de linha dentro dele são mantidas. Tudo escapado.
     */
    public PlainEmail userText(String text) {
        for (String block : text.strip().split("\\R\\s*\\R")) {
            String trimmed = block.strip();
            if (trimmed.isEmpty()) continue;
            htmlParts.add("<p>" + String.join("<br>", java.util.Arrays.stream(trimmed.split("\\R")).map(PlainEmail::escape).toList()) + "</p>");
            textParts.add(trimmed);
        }
        return this;
    }

    /** Para onde vai o "Responder" do cliente de e-mail. */
    public PlainEmail replyTo(String email) {
        this.replyTo = email;
        return this;
    }

    public PlainEmail link(String url) {
        String safe = escape(url);
        htmlParts.add("<p><a href=\"" + safe + "\">" + safe + "</a></p>");
        textParts.add(url);
        return this;
    }

    public EmailMessage build() {
        paragraph(SIGNATURE);
        String html = "<!doctype html>\n<html lang=\"pt-BR\">\n<head><meta charset=\"utf-8\"></head>\n<body>\n" + String.join("\n", htmlParts)
                + "\n</body>\n</html>\n";
        return new EmailMessage(to, subject, html, String.join("\n\n", textParts) + "\n", replyTo);
    }

    /**
     * Escapa só o que muda o sentido do HTML ({@code < > & " '}). Com o charset, os acentos ficam
     * como estão, em vez de virar entidades como {@code &aacute;}.
     */
    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    public static String greeting(String name) {
        String trimmed = name == null ? "" : name.strip();
        int space = trimmed.indexOf(' ');
        String first = space < 0 ? trimmed : trimmed.substring(0, space);
        return first.isEmpty() ? "Olá." : "Olá, " + first + ".";
    }
}
