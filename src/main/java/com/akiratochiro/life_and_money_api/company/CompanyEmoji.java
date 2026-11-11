package com.akiratochiro.life_and_money_api.company;

import java.text.BreakIterator;

/**
 * O emoji da empresa: exatamente um, como o usuário o vê. Um emoji pode ocupar vários code points
 * (a família 👨‍👩‍👧 junta pessoas com o caractere ZWJ, e a bandeira 🇧🇷 são duas letras regionais),
 * então "um" é contado em grafemas, com o {@link BreakIterator}, e não em caracteres.
 */
public final class CompanyEmoji {

    static final int MAX_LENGTH = 32;
    private static final String FIELD = "emoji";

    private CompanyEmoji() {
    }

    public static String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidCompanyDataException(FIELD, "Escolha um emoji para a empresa.");
        }
        String emoji = raw.strip();
        if (emoji.length() > MAX_LENGTH || graphemes(emoji) != 1 || !startsWithEmoji(emoji)) {
            throw new InvalidCompanyDataException(FIELD, "Use um único emoji.");
        }
        return emoji;
    }

    private static int graphemes(String text) {
        BreakIterator iterator = BreakIterator.getCharacterInstance();
        iterator.setText(text);
        int count = 0;
        while (iterator.next() != BreakIterator.DONE) {
            count++;
        }
        return count;
    }

    /**
     * {@code Character.isEmoji} sozinho não serve: os dígitos e o "#" têm a propriedade Emoji
     * (são a base dos emojis de tecla), então "1" passaria. Extended_Pictographic cobre os emojis
     * de verdade, e as letras regionais cobrem as bandeiras.
     */
    private static boolean startsWithEmoji(String text) {
        int first = text.codePointAt(0);
        boolean regionalIndicator = first >= 0x1F1E6 && first <= 0x1F1FF;
        return Character.isExtendedPictographic(first) || regionalIndicator;
    }
}
