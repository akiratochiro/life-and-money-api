package com.akiratochiro.life_and_money_api.attachment;

import java.util.Optional;

/**
 * Os tipos de arquivo aceitos, reconhecidos pelos primeiros bytes (a "assinatura" do formato), e
 * não pelo {@code Content-Type} nem pela extensão que o navegador manda, que qualquer um troca.
 */
public enum FileType {

    XML("application/xml"),
    PDF("application/pdf"),
    JPEG("image/jpeg"),
    PNG("image/png");

    private final String contentType;

    FileType(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<FileType> detect(byte[] bytes) {
        if (startsWith(bytes, 0, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(PDF);
        }
        if (startsWith(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        return looksLikeXml(bytes) ? Optional.of(XML) : Optional.empty();
    }

    public static FileType fromContentType(String contentType) {
        for (FileType type : values()) {
            if (type.contentType.equals(contentType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Tipo de arquivo desconhecido: " + contentType);
    }

    /** Um XML começa com "<", depois de um BOM UTF-8 opcional e de espaços. */
    private static boolean looksLikeXml(byte[] bytes) {
        int i = startsWith(bytes, 0, 0xEF, 0xBB, 0xBF) ? 3 : 0;
        while (i < bytes.length && Character.isWhitespace(bytes[i])) {
            i++;
        }
        return i < bytes.length && bytes[i] == '<';
    }

    private static boolean startsWith(byte[] bytes, int offset, int... signature) {
        if (bytes.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
