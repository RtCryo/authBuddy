package com.homebuddy.auth.account;

import com.homebuddy.auth.ServerMode;
import com.homebuddy.auth.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Registration and sign-in with the contract finanzBuddy used, plus the account email. */
@RestController
@RequestMapping("/auth")
@ServerMode
class AuthController {

    private final AccountService accounts;
    private final TokenService tokens;
    private final AttemptLimiter limiter;

    AuthController(AccountService accounts, TokenService tokens, AttemptLimiter limiter) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.limiter = limiter;
    }

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        limit("register", http, request.email());
        Account account = accounts.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(response(account));
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // The attempt is recorded before authenticating so parallel guesses cannot overshoot the
        // limit; a successful sign-in is released again, so only failed attempts count.
        AttemptLimiter.Attempt attempt = limit("login", http, request.email());
        Account account = accounts.authenticate(request.email(), request.password());
        limiter.release(attempt);
        return ResponseEntity.ok(response(account));
    }

    private AttemptLimiter.Attempt limit(String scope, HttpServletRequest http, String email) {
        AttemptLimiter.Attempt attempt =
                limiter.tryAcquire(scope, http.getRemoteAddr(), AccountService.normalize(email));
        if (attempt == null) {
            throw new TooManyAttemptsException(limiter.retryAfterSeconds());
        }
        return attempt;
    }

    private AuthResponse response(Account account) {
        return new AuthResponse(
                tokens.issue(account.getId(), account.getEmail()), account.getEmail());
    }
}
