package com.homebuddy.auth;

import static com.homebuddy.auth.HttpSupport.credentials;
import static com.homebuddy.auth.HttpSupport.post;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationDisabledTests extends IntegrationTest {

    @DynamicPropertySource
    static void disableRegistration(DynamicPropertyRegistry registry) {
        registry.add("auth.registration-enabled", () -> "false");
    }

    @Value("${local.server.port}")
    int port;

    @Autowired JdbcTemplate jdbc;

    @Test
    void registrationReturns403AndCreatesNothing() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";

        var reply = post(port, "/auth/register", credentials(email, "correct horse"));

        assertThat(reply.status()).isEqualTo(403);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from accounts where email_normalized = ?",
                                Integer.class,
                                email))
                .isZero();
    }

    @Test
    void signInStillWorksWhileRegistrationIsDisabled() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String hash =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                        .encode("correct horse");
        jdbc.update(
                "insert into accounts (id, email, email_normalized, password_hash)"
                        + " values (?, ?, ?, ?)",
                UUID.randomUUID(),
                email,
                email,
                hash);

        var reply = post(port, "/auth/login", credentials(email, "correct horse"));

        assertThat(reply.status()).isEqualTo(200);
    }
}
