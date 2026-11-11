package com.akiratochiro.life_and_money_api.attachment;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Um arquivo guardado no Postgres, em {@code bytea} (decisão 19). Quem usa (nota, comprovante)
 * guarda só o id: listar notas nunca carrega os bytes.
 */
@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String filename;
    private String contentType;
    private int sizeBytes;

    /** {@code CHAR(64)} no banco: o SHA-256 em hexadecimal tem sempre 64 caracteres. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64)
    private String sha256;

    private byte[] content;
    private Instant createdAt;

    protected Attachment() {
    }

    Attachment(Long userId, String filename, FileType type, byte[] content, String sha256) {
        this.userId = userId;
        this.filename = filename;
        this.contentType = type.contentType();
        this.sizeBytes = content.length;
        this.sha256 = sha256;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public byte[] getContent() {
        return content;
    }
}
