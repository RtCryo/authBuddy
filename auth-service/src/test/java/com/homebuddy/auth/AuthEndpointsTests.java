package com.homebuddy.auth;

import static com.homebuddy.auth.HttpSupport.credentials;
import static com.homebuddy.auth.HttpSupport.post;
import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.SignedJWT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

// All requests come from one client IP, so the default limit is raised out of the way here.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "auth.rate-limit.max-attempts=1000")
class AuthEndpointsTests extends IntegrationTest {

    private static final String PASSWORD = "correct horse";

    @Value("${local.server.port}")
    int port;

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @Test
    void registerCreatesAnAccountAndReturnsATokenWithTheEmail() throws Exception {
        String email = newEmail();

        var reply = post(port, "/auth/register", credentials(email, PASSWORD));

        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.json()).containsEntry("email", email).containsKey("token");
        var jwt = SignedJWT.parse((String) reply.json().get("token"));
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(accountId(email).toString());
        assertThat(jwt.getJWTClaimsSet().getStringClaim("email")).isEqualTo(email);
        String hash = storedHash(email);
        assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
    }

    @Test
    void duplicateEmailInAnyCaseReturns409AndCreatesNothing() throws Exception {
        String email = newEmail();
        assertThat(post(port, "/auth/register", credentials(email, PASSWORD)).status())
                .isEqualTo(201);

        var duplicate =
                post(
                        port,
                        "/auth/register",
                        credentials(" " + email.toUpperCase() + " ", PASSWORD));

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(count(email)).isEqualTo(1);
    }

    @Test
    void invalidInputReturns400AndCreatesNothing() throws Exception {
        String email = newEmail();
        String twoByteChars = "é".repeat(37); // 74 bytes, over the BCrypt limit

        assertThat(post(port, "/auth/register", credentials("not-an-email", PASSWORD)).status())
                .isEqualTo(400);
        assertThat(post(port, "/auth/register", credentials(email, "1234567")).status())
                .isEqualTo(400);
        assertThat(post(port, "/auth/register", credentials(email, "x".repeat(73))).status())
                .isEqualTo(400);
        assertThat(post(port, "/auth/register", credentials(email, twoByteChars)).status())
                .isEqualTo(400);
        assertThat(post(port, "/auth/register", credentials(email, "")).status()).isEqualTo(400);
        assertThat(post(port, "/auth/register", "{not json").status()).isEqualTo(400);
        assertThat(count(email)).isZero();
    }

    @Test
    void acceptsPasswordsAtTheByteLimits() throws Exception {
        assertThat(post(port, "/auth/register", credentials(newEmail(), "x".repeat(8))).status())
                .isEqualTo(201);
        assertThat(post(port, "/auth/register", credentials(newEmail(), "x".repeat(72))).status())
                .isEqualTo(201);
        assertThat(post(port, "/auth/register", credentials(newEmail(), "é".repeat(36))).status())
                .isEqualTo(201);
    }

    @Test
    void loginReturnsATokenForCorrectCredentialsInAnyEmailCase() throws Exception {
        String email = newEmail();
        post(port, "/auth/register", credentials(email, PASSWORD));

        var reply = post(port, "/auth/login", credentials(email.toUpperCase(), PASSWORD));

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json()).containsEntry("email", email).containsKey("token");
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSame401() throws Exception {
        String email = newEmail();
        post(port, "/auth/register", credentials(email, PASSWORD));

        var wrongPassword = post(port, "/auth/login", credentials(email, "wrong password"));
        var unknownEmail = post(port, "/auth/login", credentials(newEmail(), PASSWORD));
        var oversized = post(port, "/auth/login", credentials(email, "x".repeat(80)));

        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(unknownEmail.status()).isEqualTo(401);
        assertThat(oversized.status()).isEqualTo(401);
        assertThat(unknownEmail.body()).isEqualTo(wrongPassword.body());
        assertThat(wrongPassword.body()).doesNotContain(email);
    }

    @Test
    void loginRejectsInvalidInputWith400() throws Exception {
        assertThat(post(port, "/auth/login", credentials("not-an-email", PASSWORD)).status())
                .isEqualTo(400);
        assertThat(post(port, "/auth/login", credentials(newEmail(), "")).status()).isEqualTo(400);
    }

    private String newEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private int count(String email) {
        return jdbc.queryForObject(
                "select count(*) from accounts where email_normalized = ?",
                Integer.class,
                email.trim().toLowerCase());
    }

    private UUID accountId(String email) {
        return jdbc.queryForObject(
                "select id from accounts where email_normalized = ?", UUID.class, email);
    }

    private String storedHash(String email) {
        return jdbc.queryForObject(
                "select password_hash from accounts where email_normalized = ?",
                String.class,
                email);
    }
}
