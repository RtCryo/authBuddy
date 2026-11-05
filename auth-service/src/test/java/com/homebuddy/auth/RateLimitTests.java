package com.homebuddy.auth;

import static com.homebuddy.auth.HttpSupport.credentials;
import static com.homebuddy.auth.HttpSupport.post;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "auth.rate-limit.max-attempts=3")
class RateLimitTests extends IntegrationTest {

    private static final int LIMIT = 3;

    /** A clock the test can advance to let the rate-limit window pass. */
    static final class TestClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());

        void advance(Duration duration) {
            now.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired TestClock clock;

    @Test
    void tooManySignInAttemptsForOneEmailReturn429UntilTheWindowPasses() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String body = credentials(email, "wrong password");

        for (int attempt = 0; attempt < LIMIT; attempt++) {
            assertThat(post(port, "/auth/login", body).status()).isEqualTo(401);
        }
        var limited = post(port, "/auth/login", body);

        assertThat(limited.status()).isEqualTo(429);
        assertThat(limited.retryAfter()).isEqualTo("60");

        clock.advance(Duration.ofSeconds(61));

        assertThat(post(port, "/auth/login", body).status()).isEqualTo(401);
    }

    @Test
    void successfulSignInsDoNotCountAgainstTheLimit() throws Exception {
        clock.advance(Duration.ofMinutes(5));
        String body = credentials("frequent-" + UUID.randomUUID() + "@example.com", "long enough");
        assertThat(post(port, "/auth/register", body).status()).isEqualTo(201);

        for (int attempt = 0; attempt < LIMIT * 2; attempt++) {
            assertThat(post(port, "/auth/login", body).status()).isEqualTo(200);
        }
    }

    @Test
    void registrationAttemptsAreLimitedToo() throws Exception {
        clock.advance(Duration.ofMinutes(5));
        String valid = credentials("limited-" + UUID.randomUUID() + "@example.com", "long enough");
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            assertThat(post(port, "/auth/register", valid).status()).isIn(201, 409);
        }

        assertThat(post(port, "/auth/register", valid).status()).isEqualTo(429);

        // Invalid input is rejected by validation before it reaches the limiter.
        String invalid = credentials("short-" + UUID.randomUUID() + "@example.com", "short");
        assertThat(post(port, "/auth/register", invalid).status()).isEqualTo(400);
    }
}
