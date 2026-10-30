package com.akiratochiro.life_and_money_api;

import com.akiratochiro.life_and_money_api.email.EmailMessage;
import com.akiratochiro.life_and_money_api.email.EmailSender;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Guarda os e-mails em vez de enviá-los, para os testes conferirem o que sairia. */
public class RecordingEmailSender implements EmailSender {

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(EmailMessage message) {
        sent.add(message);
    }

    public List<EmailMessage> messagesTo(String to) {
        return sent.stream().filter(message -> message.to().equals(to)).toList();
    }

    /**
     * Espera o n-ésimo e-mail para o destinatário (contando de 1). O envio é assíncrono, então o
     * e-mail chega um pouco depois da resposta da API.
     */
    public EmailMessage awaitMessageTo(String to, int count) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            List<EmailMessage> messages = messagesTo(to);
            if (messages.size() >= count) {
                return messages.get(count - 1);
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Nenhum e-mail número " + count + " para " + to + " em 5 s");
    }

    public void clear() {
        sent.clear();
    }
}
