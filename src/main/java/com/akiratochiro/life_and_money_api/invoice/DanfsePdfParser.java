package com.akiratochiro.life_and_money_api.invoice;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plano B, para quem só tem o PDF da nota (DANFSe): extrai o texto com o PDFBox e procura os
 * campos. O resultado é sempre uma sugestão, que o usuário confere antes de salvar.
 *
 * <p>Por enquanto reconhece só a chave de acesso, cujo formato (50 dígitos, às vezes em grupos)
 * não depende do desenho do PDF. Os outros campos dependem dos rótulos do DANFSe e entram quando
 * houver um PDF real para conferi-los (plano da Fase 2, item 1.3).
 */
public final class DanfsePdfParser {

    /** O texto das primeiras páginas basta: o DANFSe tem uma página, e um PDF enorme não é nota. */
    private static final int MAX_PAGES = 3;

    /** 50 dígitos seguidos, ou separados por espaços e pontos, como a chave costuma ser impressa. */
    private static final Pattern ACCESS_KEY = Pattern.compile("(?<![0-9])((?:[0-9][ .]?){49}[0-9])(?![0-9])");

    private DanfsePdfParser() {
    }

    /** @return vazio quando nada foi reconhecido (PDF escaneado, protegido ou de outro documento) */
    public static Optional<ParsedInvoice> parse(byte[] pdf) {
        String text = text(pdf);
        if (text == null) {
            return Optional.empty();
        }
        String accessKey = accessKey(text);
        if (accessKey == null) {
            return Optional.empty();
        }
        return Optional.of(new ParsedInvoice(null, accessKey, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, false));
    }

    static String accessKey(String text) {
        Matcher matcher = ACCESS_KEY.matcher(text);
        while (matcher.find()) {
            String digits = matcher.group(1).replaceAll("[ .]", "");
            if (digits.length() == 50) {
                return digits;
            }
        }
        return null;
    }

    /**
     * O cache dos fluxos do PDF vai para arquivo temporário, e não para a memória: um PDF de 2 MB
     * comprimido pode expandir bastante, e a instância gratuita tem pouca RAM.
     */
    static String text(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf, "", null, null, IOUtils.createTempFileOnlyStreamCache())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(MAX_PAGES, document.getNumberOfPages()));
            String text = stripper.getText(document);
            return text.isBlank() ? null : text;
        } catch (IOException e) {
            // PDF protegido por senha, corrompido ou que não é PDF: nada reconhecido, segue à mão.
            return null;
        }
    }
}
