package com.homebuddy.auth;

import static com.homebuddy.auth.HttpSupport.credentials;
import static com.homebuddy.auth.HttpSupport.post;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Runs the real {@code import-legacy} command against the test database. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "auth.rate-limit.max-attempts=1000")
class LegacyImportTests extends IntegrationTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${local.server.port}")
    int port;

    @Autowired JdbcTemplate jdbc;

    @Test
    void importsNewUsersWhoSignInWithTheirOldPasswords(@TempDir Path dir) throws Exception {
        String first = email();
        String second = email();
        Path file =
                csv(
                        dir,
                        "email,password_hash",
                        first + "," + encoder.encode("old password one"),
                        "\"" + second + "\",\"" + encoder.encode("old password two") + "\"");

        Command result = runCommand("--file=" + file);

        assertThat(result.exitCode()).isZero();
        assertThat(result.out()).contains("created=2 skipped=0 invalid=0");
        assertThat(post(port, "/auth/login", credentials(first, "old password one")).status())
                .isEqualTo(200);
        assertThat(post(port, "/auth/login", credentials(second, "old password two")).status())
                .isEqualTo(200);
        assertThat(post(port, "/auth/login", credentials(first, "old password two")).status())
                .isEqualTo(401);
    }

    @Test
    void secondRunCreatesAndChangesNothing(@TempDir Path dir) throws Exception {
        String email = email();
        String hash = encoder.encode("old password");
        Path file = csv(dir, "email,password_hash", email + "," + hash);
        runCommand("--file=" + file);
        String before = storedHash(email);

        Command second = runCommand("--file=" + file);

        assertThat(second.out()).contains("created=0 skipped=1 invalid=0");
        assertThat(storedHash(email)).isEqualTo(before);
        assertThat(count(email)).isEqualTo(1);
    }

    @Test
    void existingAndCaseVariantEmailsAreSkippedWithoutChangingTheAccount(@TempDir Path dir)
            throws Exception {
        String email = email();
        String original = encoder.encode("registered password");
        jdbc.update(
                "insert into accounts (id, email, email_normalized, password_hash)"
                        + " values (?, ?, ?, ?)",
                UUID.randomUUID(),
                email,
                email,
                original);
        Path file =
                csv(
                        dir,
                        email.toUpperCase() + "," + encoder.encode("legacy password"),
                        email + "," + encoder.encode("another legacy password"));

        Command result = runCommand("--file=" + file);

        assertThat(result.out()).contains("created=0 skipped=2 invalid=0");
        assertThat(storedHash(email)).isEqualTo(original);
    }

    @Test
    void malformedRowsAreCountedWithoutPrintingThemAndOthersAreImported(@TempDir Path dir)
            throws Exception {
        String good = email();
        String badEmail = "not-an-email-" + UUID.randomUUID();
        String badHash = email();
        Path file =
                csv(
                        dir,
                        "email,password_hash",
                        good + "," + encoder.encode("old password"),
                        badEmail + "," + encoder.encode("old password"),
                        badHash + ",plaintext-instead-of-bcrypt",
                        "missing-comma-column",
                        ",");

        Command result = runCommand("--file=" + file);

        assertThat(result.exitCode()).isZero();
        assertThat(result.out()).contains("created=1 skipped=0 invalid=4");
        assertThat(result.out() + result.err())
                .doesNotContain(good)
                .doesNotContain(badEmail)
                .doesNotContain(badHash)
                .doesNotContain("$2a$");
        assertThat(count(good)).isEqualTo(1);
        assertThat(count(badHash)).isZero();
    }

    @Test
    void acceptsAHeaderlessFileWithABom(@TempDir Path dir) throws Exception {
        String email = email();
        Path file = csv(dir, "﻿" + email + "," + encoder.encode("old password"));

        Command result = runCommand("--file=" + file);

        assertThat(result.out()).contains("created=1 skipped=0 invalid=0");
    }

    @Test
    void failsWithUsageWhenTheFileArgumentIsMissingOrUnreadable(@TempDir Path dir) {
        Command missing = runCommand();
        Command unreadable = runCommand("--file=" + dir.resolve("does-not-exist.csv"));

        assertThat(missing.exitCode()).isEqualTo(2);
        assertThat(missing.err()).contains("Usage: import-legacy");
        assertThat(unreadable.exitCode()).isEqualTo(1);
        assertThat(unreadable.err()).contains("cannot be read");
    }

    private record Command(int exitCode, String out, String err) {}

    private Command runCommand(String... extra) {
        String[] args = new String[4 + extra.length];
        args[0] = AuthServiceApplication.IMPORT_COMMAND;
        args[1] = "--spring.datasource.url=" + POSTGRES.getJdbcUrl();
        args[2] = "--spring.datasource.username=" + POSTGRES.getUsername();
        args[3] = "--spring.datasource.password=" + POSTGRES.getPassword();
        System.arraycopy(extra, 0, args, 4, extra.length);

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true));
            System.setErr(new PrintStream(err, true));
            int exitCode = AuthServiceApplication.runImport(args);
            return new Command(exitCode, out.toString(), err.toString());
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static Path csv(Path dir, String... lines) throws Exception {
        return Files.writeString(dir.resolve(UUID.randomUUID() + ".csv"), String.join("\n", lines));
    }

    private static String email() {
        return "legacy-" + UUID.randomUUID() + "@example.com";
    }

    private int count(String email) {
        return jdbc.queryForObject(
                "select count(*) from accounts where email_normalized = ?",
                Integer.class,
                email.toLowerCase());
    }

    private String storedHash(String email) {
        return jdbc.queryForObject(
                "select password_hash from accounts where email_normalized = ?",
                String.class,
                email.toLowerCase());
    }
}
