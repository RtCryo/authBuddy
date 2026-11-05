package com.homebuddy.auth;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for integration tests: one PostgreSQL container for the whole test JVM and generated signing
 * keys (a current key and one previous public key).
 */
abstract class IntegrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    static final TestKeys.Pem CURRENT = TestKeys.generate();
    static final TestKeys.Pem PREVIOUS = TestKeys.generate();

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("auth.signing-key", CURRENT::privateKey);
        registry.add("auth.previous-public-keys", () -> PREVIOUS.publicKey().replace("\n", "\\n"));
    }
}
