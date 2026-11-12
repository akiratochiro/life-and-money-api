package com.akiratochiro.life_and_money_api.calendar;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O calendário define a interface e não conhece quem o alimenta (decisão 31): nenhum import de outra
 * feature, só de {@code shared}. Assim uma fonte nova entra sem mexer nele.
 */
class CalendarIndependenceTest {

    private static final Pattern FEATURE_IMPORT = Pattern.compile("import com\\.akiratochiro\\.life_and_money_api\\.(\\w+)\\.");

    @Test
    void theCalendarImportsNoOtherFeature() throws IOException {
        Path dir = Path.of("src/main/java/com/akiratochiro/life_and_money_api/calendar");
        try (Stream<Path> files = Files.list(dir)) {
            List<String> offenders = files.filter(f -> f.toString().endsWith(".java"))
                    .flatMap(f -> {
                        try {
                            Matcher m = FEATURE_IMPORT.matcher(Files.readString(f));
                            return m.results().map(r -> r.group(1)).filter(pkg -> !pkg.equals("shared"))
                                    .map(pkg -> f.getFileName() + " → " + pkg).toList().stream();
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
            assertThat(offenders).as("imports de outras features no calendário").isEmpty();
        }
    }
}
