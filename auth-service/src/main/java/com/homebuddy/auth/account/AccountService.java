package com.homebuddy.auth.account;

import com.homebuddy.auth.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class AccountService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

    /** Verified against when the email is unknown, so unknown and wrong-password cost the same. */
    private final String dummyHash;

    AccountService(
            AccountRepository accounts,
            PasswordEncoder passwordEncoder,
            AuthProperties properties) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /**
     * Not transactional on purpose: the BCrypt hash is computed without holding a database
     * connection, and the existence check and insert each run in their own repository transaction.
     */
    Account register(String email, String password) {
        if (!properties.registrationEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registration is disabled");
        }
        String normalized = normalize(email);
        // The existence check is a cheap fast path; the unique index is the authoritative guard
        // when two requests race, so a violation maps to the same 409.
        if (accounts.existsByEmailNormalized(normalized)) {
            throw emailTaken();
        }
        try {
            return accounts.saveAndFlush(
                    new Account(email, normalized, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
    }

    @Transactional(readOnly = true)
    Account authenticate(String email, String password) {
        Optional<Account> account = accounts.findByEmailNormalized(normalize(email));
        boolean oversized = password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
        String hash = account.map(Account::getPasswordHash).orElse(dummyHash);
        boolean matches = !oversized && passwordEncoder.matches(password, hash);
        if (account.isEmpty() || !matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return account.get();
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException emailTaken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
    }
}
