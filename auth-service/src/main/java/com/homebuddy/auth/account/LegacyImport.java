package com.homebuddy.auth.account;

import jakarta.validation.Validator;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Imports finanzBuddy users (email and BCrypt hash) as homeBuddy accounts.
 *
 * <p>Each row is inserted independently with {@code ON CONFLICT DO NOTHING}, so a run is idempotent
 * and no exception message can carry an email or a hash. Only counts are reported.
 */
@Service
class LegacyImport {

    /** A BCrypt hash with a cost of 04 to 31; other costs make sign-in verification throw. */
    private static final Pattern BCRYPT =
            Pattern.compile("^\\$2[aby]?\\$(0[4-9]|[12]\\d|3[01])\\$[./A-Za-z0-9]{53}$");

    /** Counts of imported, already present, and rejected rows. */
    record Result(int created, int skipped, int invalid) {

        String report() {
            return "created=" + created + " skipped=" + skipped + " invalid=" + invalid;
        }
    }

    private final JdbcTemplate jdbc;
    private final Validator validator;

    LegacyImport(JdbcTemplate jdbc, Validator validator) {
        this.jdbc = jdbc;
        this.validator = validator;
    }

    Result importFrom(BufferedReader reader) throws IOException {
        int created = 0;
        int skipped = 0;
        int invalid = 0;
        boolean first = true;
        String line;
        while ((line = reader.readLine()) != null) {
            if (first) {
                first = false;
                line = stripBom(line);
                if (isHeader(line)) {
                    continue;
                }
            }
            if (line.isBlank()) {
                continue;
            }
            int comma = line.indexOf(',');
            String email = comma < 0 ? "" : unquote(line.substring(0, comma));
            String hash = comma < 0 ? "" : unquote(line.substring(comma + 1));
            if (!validEmail(email) || !BCRYPT.matcher(hash).matches()) {
                invalid++;
            } else if (insert(email, hash)) {
                created++;
            } else {
                skipped++;
            }
        }
        return new Result(created, skipped, invalid);
    }

    private boolean insert(String email, String hash) {
        int rows =
                jdbc.update(
                        "insert into accounts (id, email, email_normalized, password_hash)"
                                + " values (?, ?, ?, ?)"
                                + " on conflict (email_normalized) do nothing",
                        UUID.randomUUID(),
                        email,
                        AccountService.normalize(email),
                        hash);
        return rows == 1;
    }

    private boolean validEmail(String email) {
        return !email.isEmpty()
                && validator.validateValue(RegisterRequest.class, "email", email).isEmpty();
    }

    private static boolean isHeader(String line) {
        int comma = line.indexOf(',');
        return comma > 0
                && unquote(line.substring(0, comma)).equalsIgnoreCase("email")
                && unquote(line.substring(comma + 1)).equalsIgnoreCase("password_hash");
    }

    private static String stripBom(String line) {
        return line.startsWith("﻿") ? line.substring(1) : line;
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
