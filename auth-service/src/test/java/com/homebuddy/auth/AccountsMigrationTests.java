package com.homebuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class AccountsMigrationTests {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void createsAccountsTableWithExpectedColumns() {
        var columns =
                jdbc.queryForList(
                        "select column_name from information_schema.columns"
                                + " where table_name = 'accounts'",
                        String.class);

        assertThat(columns)
                .containsExactlyInAnyOrder(
                        "id",
                        "email",
                        "email_normalized",
                        "password_hash",
                        "created_at",
                        "updated_at");
    }

    @Test
    void rejectsDuplicateNormalizedEmail() {
        insert("Family@Example.com", "family@example.com");

        assertThatThrownBy(() -> insert("family@example.com", "family@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rollsBackAndReappliesCleanly() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Database database =
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase =
                    new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);

            liquibase.rollback(1, "");
            assertThat(tableExists()).isFalse();

            liquibase.update("");
            assertThat(tableExists()).isTrue();
        }
    }

    private void insert(String email, String normalized) {
        jdbc.update(
                "insert into accounts (id, email, email_normalized, password_hash)"
                        + " values (?, ?, ?, ?)",
                UUID.randomUUID(),
                email,
                normalized,
                "$2a$10$" + "x".repeat(53));
    }

    private boolean tableExists() {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select exists (select 1 from information_schema.tables"
                                + " where table_name = 'accounts')",
                        Boolean.class));
    }
}
