package com.akiratochiro.life_and_money_api.attachment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Guarda arquivos no Postgres com três limites: 2 MB por arquivo, os tipos que quem chama aceita
 * (conferidos pelos primeiros bytes) e uma cota de 50 MB por usuário, que protege o 1 GB do plano
 * gratuito do Neon de um único usuário (decisão 19).
 */
@Service
public class AttachmentService {

    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final long QUOTA_BYTES = 50L * 1024 * 1024;

    private final AttachmentRepository repository;

    public AttachmentService(AttachmentRepository repository) {
        this.repository = repository;
    }

    /**
     * Confere tamanho e tipo sem guardar nada: a pré-visualização das notas usa isso antes de ler
     * o arquivo.
     */
    public static FileType check(String filename, byte[] content, Set<FileType> accepted) {
        String name = displayName(filename);
        if (content.length == 0) {
            throw new InvalidAttachmentException("O arquivo " + name + " está vazio.");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new InvalidAttachmentException("O arquivo " + name + " tem mais de 2 MB.");
        }
        FileType type = FileType.detect(content).filter(accepted::contains).orElseThrow(() ->
                new InvalidAttachmentException("O arquivo " + name + " não é " + describe(accepted) + "."));
        return type;
    }

    @Transactional
    public Attachment store(Long userId, String filename, byte[] content, Set<FileType> accepted) {
        FileType type = check(filename, content, accepted);
        if (repository.sumSizeByUserId(userId) + content.length > QUOTA_BYTES) {
            throw new InvalidAttachmentException("Você atingiu o limite de 50 MB de arquivos. Apague notas ou "
                    + "comprovantes antigos para liberar espaço.");
        }
        return repository.save(new Attachment(userId, cleanName(filename, type), type, content, sha256(content)));
    }

    /** O arquivo do usuário; quem chama já conferiu que o registro que aponta para ele é do usuário. */
    @Transactional(readOnly = true)
    public Attachment read(Long userId, Long attachmentId) {
        return repository.findByIdAndUserId(attachmentId, userId).orElseThrow();
    }

    @Transactional
    public void delete(Long userId, Long attachmentId) {
        repository.findByIdAndUserId(attachmentId, userId).ifPresent(repository::delete);
    }

    @Transactional(readOnly = true)
    public AttachmentUsage usage(Long userId) {
        return new AttachmentUsage(repository.sumSizeByUserId(userId), QUOTA_BYTES);
    }

    /** Para a exclusão da conta, depois de quem aponta para os anexos (notas, despesas). */
    @Transactional
    public void deleteAllOf(Long userId) {
        repository.deleteAllByUserId(userId);
    }

    /**
     * O nome sem caminho ("C:\fakepath\nota.xml" vira "nota.xml"), sem caracteres de controle e com
     * no máximo 255 caracteres. Vai no Content-Disposition do download, então não pode carregar
     * quebra de linha.
     */
    static String cleanName(String filename, FileType type) {
        String name = filename == null ? "" : filename.replaceAll(".*[/\\\\]", "").replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            name = "arquivo." + type.name().toLowerCase(java.util.Locale.ROOT);
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String displayName(String filename) {
        return filename == null || filename.isBlank() ? "enviado" : "\"" + cleanName(filename, FileType.XML) + "\"";
    }

    private static String describe(Set<FileType> accepted) {
        if (accepted.equals(Set.of(FileType.XML, FileType.PDF))) {
            return "um XML ou PDF";
        }
        return "um XML, PDF, JPEG ou PNG";
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
