package com.homebuddy.auth;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Identity service configuration.
 *
 * @param signingKey current RSA private key as PKCS#8 PEM; literal {@code \n} sequences are
 *     accepted so the key fits into a single environment variable
 * @param signingKeyFile path of a file holding the current private key, used when {@code
 *     signingKey} is blank (for Docker secrets)
 * @param previousPublicKeys public keys (X.509 PEM) that stay published in the JWKS after a
 *     rotation
 * @param issuer token issuer claim
 * @param audiences token audience claim
 * @param tokenExpirationMs token lifetime in milliseconds
 * @param registrationEnabled whether {@code POST /auth/register} creates accounts; kept {@code
 *     false} during the legacy migration so nobody can claim an existing email first
 * @param rateLimit limits for sign-in and registration attempts
 */
@ConfigurationProperties("auth")
public record AuthProperties(
        String signingKey,
        String signingKeyFile,
        List<String> previousPublicKeys,
        @DefaultValue("homebuddy-auth") String issuer,
        @DefaultValue({"finanzbuddy", "calendarbuddy"}) List<String> audiences,
        @DefaultValue("86400000") long tokenExpirationMs,
        @DefaultValue("true") boolean registrationEnabled,
        @DefaultValue RateLimit rateLimit) {

    /**
     * Sliding-window limit applied separately per client IP and per email.
     *
     * @param maxAttempts attempts allowed within the window
     * @param window window length
     */
    public record RateLimit(
            @DefaultValue("10") int maxAttempts, @DefaultValue("1m") Duration window) {}
}
