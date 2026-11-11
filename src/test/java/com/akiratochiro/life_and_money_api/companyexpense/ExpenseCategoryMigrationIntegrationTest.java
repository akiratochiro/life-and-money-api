package com.akiratochiro.life_and_money_api.companyexpense;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** A V15 dá as categorias padrão às empresas cadastradas antes dela, como o evento faz com as novas. */
class ExpenseCategoryMigrationIntegrationTest extends IntegrationTest {

    private static final String SCHEMA = "v15_seed";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void dropSchema() {
        jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    }

    private void migrateTo(String version) {
        Flyway.configure().dataSource(dataSource).schemas(SCHEMA).locations("classpath:db/migration")
                .target(version).load().migrate();
    }

    @Test
    void existingCompaniesGetTheDefaultCategories() {
        migrateTo("14");
        jdbc.update("insert into " + SCHEMA + ".users (id, name, email, password_hash) values (1, 'Ana', 'ana@x.dev', 'x')");
        jdbc.update("insert into " + SCHEMA + ".companies (id, user_id, name, emoji, opening_date, opening_balance_date) "
                + "values (10, 1, 'Antiga', '💼', date '2024-03-10', date '2024-03-10')");

        migrateTo("15");

        assertThat(jdbc.queryForList("select name from " + SCHEMA + ".company_expense_categories where company_id = 10 order by name",
                String.class))
                .containsExactly("Aluguel", "Contador", "Equipamentos", "Impostos", "Outros", "Salários e encargos", "Software");
        assertThat(jdbc.queryForObject("select system_key from " + SCHEMA + ".company_expense_categories where name = 'Impostos'",
                String.class)).isEqualTo("TAXES");
        assertThat(jdbc.queryForObject("select counts_as_payroll from " + SCHEMA
                + ".company_expense_categories where name = 'Salários e encargos'", Boolean.class)).isTrue();
    }
}
