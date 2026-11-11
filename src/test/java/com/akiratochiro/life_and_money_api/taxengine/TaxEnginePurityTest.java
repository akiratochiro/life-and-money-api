package com.akiratochiro.life_and_money_api.taxengine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O motor tributário é domínio puro: nenhum import de Spring nem de JPA. Assim ele é testado com
 * exemplos feitos à mão, sem contexto nem banco. Lê o código-fonte em vez de usar o ArchUnit, para
 * não acrescentar dependência.
 */
class TaxEnginePurityTest {

    @Test
    void theTaxEngineImportsNoFrameworkNorPersistence() throws IOException {
        Path dir = Path.of("src/main/java/com/akiratochiro/life_and_money_api/taxengine");
        try (Stream<Path> files = Files.list(dir)) {
            List<String> offenders = files.filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> {
                        try {
                            String code = Files.readString(f);
                            return code.contains("import org.springframework") || code.contains("import jakarta.persistence")
                                    || code.contains("import org.hibernate");
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(f -> f.getFileName().toString())
                    .toList();
            assertThat(offenders).as("arquivos do motor que importam framework").isEmpty();
        }
    }
}
