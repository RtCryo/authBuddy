package com.homebuddy.auth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business metrics for the central homeBuddy monitoring. The only label is {@code outcome} from a
 * fixed set, so the series count never depends on accounts, e-mail addresses, or client addresses.
 */
@Component
@ServerMode
public class AuthMetrics {

    /** Outcomes of a sign-in attempt. */
    public enum SignIn {
        SUCCESS("success"),
        INVALID_CREDENTIALS("invalid_credentials"),
        RATE_LIMITED("rate_limited");

        private final String label;

        SignIn(String label) {
            this.label = label;
        }
    }

    /** Outcomes of a registration attempt. */
    public enum Registration {
        SUCCESS("success"),
        REJECTED("rejected"),
        DISABLED("disabled");

        private final String label;

        Registration(String label) {
            this.label = label;
        }
    }

    private final Counter[] signIns = new Counter[SignIn.values().length];
    private final Counter[] registrations = new Counter[Registration.values().length];
    private final Counter tokens;

    AuthMetrics(MeterRegistry registry) {
        for (SignIn outcome : SignIn.values()) {
            signIns[outcome.ordinal()] =
                    Counter.builder("authbuddy.signin.attempts")
                            .description("Sign-in attempts by outcome")
                            .tag("outcome", outcome.label)
                            .register(registry);
        }
        for (Registration outcome : Registration.values()) {
            registrations[outcome.ordinal()] =
                    Counter.builder("authbuddy.registrations")
                            .description("Registration attempts by outcome")
                            .tag("outcome", outcome.label)
                            .register(registry);
        }
        tokens =
                Counter.builder("authbuddy.tokens.issued")
                        .description("Account tokens issued")
                        .register(registry);
    }

    public void signIn(SignIn outcome) {
        signIns[outcome.ordinal()].increment();
    }

    public void registration(Registration outcome) {
        registrations[outcome.ordinal()].increment();
    }

    public void tokenIssued() {
        tokens.increment();
    }
}
