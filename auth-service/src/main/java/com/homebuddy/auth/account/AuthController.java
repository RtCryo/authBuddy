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
        limit("login", http, request.email());
        Account account = accounts.authenticate(request.email(), request.password());
        return ResponseEntity.ok(response(account));
    }

    private void limit(String scope, HttpServletRequest http, String email) {
        if (!limiter.tryAcquire(scope, http.getRemoteAddr(), AccountService.normalize(email))) {
            throw new TooManyAttemptsException(limiter.retryAfterSeconds());
        }
    }

    private AuthResponse response(Account account) {
        return new AuthResponse(
                tokens.issue(account.getId(), account.getEmail()), account.getEmail());
    }
}
