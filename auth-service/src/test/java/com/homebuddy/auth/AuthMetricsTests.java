package com.homebuddy.auth;

import static com.homebuddy.auth.HttpSupport.credentials;
import static com.homebuddy.auth.HttpSupport.post;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "auth.rate-limit.max-attempts=1000",
            "management.defaults.metrics.export.enabled=true",
            "management.prometheus.metrics.export.enabled=true"
        })
class AuthMetricsTests extends IntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired MeterRegistry registry;

    private double count(String name, String outcome) {
        var search = registry.find(name);
        if (outcome != null) {
            search = search.tag("outcome", outcome);
        }
        var counter = search.counter();
        return counter == null ? 0 : counter.count();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void signInRegistrationAndTokenOutcomesAreCounted() throws Exception {
        String email = "metrics-" + UUID.randomUUID() + "@example.com";
        String body = credentials(email, "correct horse");
        double registered = count("authbuddy.registrations", "success");
        double rejected = count("authbuddy.registrations", "rejected");
        double ok = count("authbuddy.signin.attempts", "success");
        double invalid = count("authbuddy.signin.attempts", "invalid_credentials");
        double tokens = count("authbuddy.tokens.issued", null);

        assertThat(post(port, "/auth/register", body).status()).isEqualTo(201);
        assertThat(post(port, "/auth/register", body).status()).isEqualTo(409);
        assertThat(post(port, "/auth/login", body).status()).isEqualTo(200);
        assertThat(post(port, "/auth/login", credentials(email, "wrong password")).status())
                .isEqualTo(401);

        assertThat(count("authbuddy.registrations", "success")).isEqualTo(registered + 1);
        assertThat(count("authbuddy.registrations", "rejected")).isEqualTo(rejected + 1);
        assertThat(count("authbuddy.signin.attempts", "success")).isEqualTo(ok + 1);
        assertThat(count("authbuddy.signin.attempts", "invalid_credentials"))
                .isEqualTo(invalid + 1);
        // One token for the registration and one for the sign-in.
        assertThat(count("authbuddy.tokens.issued", null)).isEqualTo(tokens + 2);
    }

    @Test
    void businessMetricsCarryOnlyTheOutcomeLabel() throws Exception {
        String email = "labels-" + UUID.randomUUID() + "@example.com";
        post(port, "/auth/register", credentials(email, "correct horse"));
        post(port, "/auth/login", credentials(email, "wrong password"));

        for (Meter meter : registry.getMeters()) {
            if (meter.getId().getName().startsWith("authbuddy.")) {
                assertThat(meter.getId().getTags())
                        .allSatisfy(tag -> assertThat(tag.getKey()).isEqualTo("outcome"));
            }
        }
    }

    @Test
    void prometheusEndpointIsPublicAndOtherActuatorPathsStayDenied() throws Exception {
        String email = "scrape-" + UUID.randomUUID() + "@example.com";
        post(port, "/auth/login", credentials(email, "wrong password"));

        var scrape = get("/actuator/prometheus");

        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.body())
                .contains("authbuddy_signin_attempts_total", "jvm_memory_used_bytes", "outcome=");
        assertThat(scrape.body()).doesNotContain("@example.com");
        assertThat(get("/actuator/env").statusCode()).isIn(401, 403, 404);
        assertThat(get("/actuator/metrics").statusCode()).isIn(401, 403, 404);
    }
}
