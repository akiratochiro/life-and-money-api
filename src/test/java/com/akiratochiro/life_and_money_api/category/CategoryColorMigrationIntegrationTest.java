package com.akiratochiro.life_and_money_api.category;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O backfill da V10 contra dados no formato antigo: um schema à parte é migrado até a V9,
 * recebe categorias sem cor nem ícone, e só então a V10 roda.
 */
class CategoryColorMigrationIntegrationTest extends IntegrationTest {

    private static final String SCHEMA = "v10_backfill";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void dropSchema() {
        jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    }

    private Flyway flywayUpTo(String version) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target(version)
                .load();
    }

    @Test
    void existingCategoriesGetAColorAndIconFromTheirNameOrType() {
        // given: o banco como era antes da V10
        flywayUpTo("9").migrate();
        jdbc.update("insert into " + SCHEMA + ".users (id, name, email, password_hash) values (1, 'Ana', 'ana@x.dev', 'x')");
        jdbc.update("""
                insert into %s.categories (user_id, name, type) values
                  (1, 'Moradia', 'EXPENSE'), (1, 'Salário', 'INCOME'), (1, 'Investimentos', 'SAVING'),
                  (1, 'Academia', 'EXPENSE'), (1, 'Freela', 'INCOME'), (1, 'Viagem', 'SAVING')
                """.formatted(SCHEMA));

        // when
        flywayUpTo("10").migrate();

        // then
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select name, color, icon from " + SCHEMA + ".categories order by id");
        assertThat(rows).extracting(row -> row.get("name") + "=" + row.get("color") + "/" + row.get("icon"))
                .containsExactly(
                        "Moradia=PEACH/HOME",
                        "Salário=MINT/SALARY",
                        "Investimentos=PINK/PIGGY_BANK",
                        "Academia=CORAL/OTHER",
                        "Freela=MINT/COIN",
                        "Viagem=PINK/PIGGY_BANK");
    }
}
