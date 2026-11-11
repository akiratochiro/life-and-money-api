package com.akiratochiro.life_and_money_api.invoice;

import com.akiratochiro.life_and_money_api.attachment.FileType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * O que a pré-visualização diz sobre um arquivo. Nada foi gravado: salvar é um segundo pedido.
 *
 * @param fileType o tipo reconhecido; um PDF é recusado com a orientação de enviar o XML
 */
public record InvoicePreview(
        @Schema(requiredMode = REQUIRED) String filename,
        @Schema(description = "Ausente quando o arquivo não foi reconhecido") FileType fileType,
        @Schema(requiredMode = REQUIRED) Status status,
        @Schema(description = "O que fazer com este arquivo, quando há algo a dizer") String message,
        @Schema(requiredMode = REQUIRED, description = "Avisos que não impedem salvar") List<String> warnings,
        @Schema(description = "O que foi lido do XML") InvoiceData fields
) {
    public enum Status {
        /** Lida do XML e pronta para salvar, com os valores travados. */
        READY,
        /** A empresa já tem esta nota. */
        DUPLICATE,
        /** Não pode entrar: PDF (só XML, por enquanto), arquivo inválido, outro CNPJ, homologação, antes da abertura. */
        REJECTED
    }

    static InvoicePreview rejected(String filename, FileType type, String message) {
        return new InvoicePreview(filename, type, Status.REJECTED, message, List.of(), null);
    }
}
