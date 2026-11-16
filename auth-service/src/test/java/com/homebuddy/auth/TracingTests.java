package com.homebuddy.auth;

import static com.homebuddy.auth.HttpSupport.credentials;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "auth.rate-limit.max-attempts=1000",
            // Export immediately so the test does not wait for the batch delay.
            "management.opentelemetry.tracing.export.schedule-delay=10ms"
        })
@ExtendWith(OutputCaptureExtension.class)
class TracingTests extends IntegrationTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String PARENT_ID = "00f067aa0ba902b7";

    @Value("${local.server.port}")
    int port;

    @Autowired ApplicationContext context;
    @Autowired Tracer tracer;
    @Autowired CollectingSpanExporter exported;

    @BeforeEach
    void clear() {
        exported.spans.clear();
    }

    @Test
    void nothingIsExportedOverOtlpWithoutAnEndpoint() {
        assertThat(context.getBeansOfType(OtlpHttpSpanExporter.class)).isEmpty();
        assertThat(context.getBeansOfType(OtlpMeterRegistry.class)).isEmpty();
    }

    @Test
    void signInContinuesTheCallersTraceWithTheServiceResource() throws Exception {
        String email = "trace-" + UUID.randomUUID() + "@example.com";
        String password = "trace secret " + UUID.randomUUID();

        int status = send("POST", "/auth/login", credentials(email, password), true);

        assertThat(status).isEqualTo(401);
        SpanData span = awaitSpan(TRACE_ID);
        assertThat(span.getParentSpanId()).isEqualTo(PARENT_ID);
        assertThat(span.getName()).containsIgnoringCase("post").contains("/auth/login");
        assertThat(span.getResource().getAttribute(AttributeKey.stringKey("service.name")))
                .isEqualTo("authbuddy-auth-service");
        assertThat(span.getResource().getAttribute(AttributeKey.stringKey("service.namespace")))
                .isEqualTo("authbuddy");
        // No attribute holds the credentials of the request.
        assertThat(span.getAttributes().asMap().values())
                .allSatisfy(
                        value ->
                                assertThat(String.valueOf(value))
                                        .doesNotContain(email)
                                        .doesNotContain(password));
    }

    @Test
    void signInLogLineCarriesTheTraceIdAndNoPersonalData(CapturedOutput output) throws Exception {
        String email = "logline-" + UUID.randomUUID() + "@example.com";

        send("POST", "/auth/login", credentials(email, "wrong password"), true);

        assertThat(output.getOut())
                .containsPattern(
                        Pattern.quote("[" + TRACE_ID + "-")
                                + "[0-9a-f]{16}\\].*Sign-in rejected: invalid credentials")
                .doesNotContain(email);
    }

    @Test
    void actuatorRequestsAreNotExported() throws Exception {
        assertThat(send("GET", "/actuator/health", null, false)).isEqualTo(200);
        String email = "after-" + UUID.randomUUID() + "@example.com";
        send("POST", "/auth/login", credentials(email, "wrong password"), false);

        // The later sign-in is exported, so the health check would have been exported by now.
        await().atMost(Duration.ofSeconds(10))
                .until(
                        () ->
                                exported.spans.stream()
                                        .anyMatch(s -> s.getName().contains("/auth/login")));
        assertThat(exported.spans).noneMatch(s -> s.getName().contains("/actuator"));
        // No span of the health check is exported without its server span either.
        assertThat(
                        exported.spans.stream()
                                .collect(Collectors.groupingBy(SpanData::getTraceId))
                                .values())
                .allSatisfy(
                        trace ->
                                assertThat(trace)
                                        .anyMatch(span -> span.getKind() == SpanKind.SERVER));
    }

    @Test
    void logLinesInsideASpanCarryTheTraceAndSpanId(CapturedOutput output) {
        Span span = tracer.nextSpan().name("log-correlation").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            LoggerFactory.getLogger(TracingTests.class).info("inside a traced request");
        } finally {
            span.end();
        }

        String correlation = "[" + span.context().traceId() + "-" + span.context().spanId() + "]";
        assertThat(span.context().traceId()).matches("[0-9a-f]{32}");
        assertThat(span.context().spanId()).matches("[0-9a-f]{16}");
        assertThat(output.getOut())
                .containsPattern(Pattern.quote(correlation) + ".*inside a traced request");
    }

    private int send(String method, String path, String json, boolean withParent) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (withParent) {
            request.header("traceparent", "00-" + TRACE_ID + "-" + PARENT_ID + "-01");
        }
        if (json == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        return HttpClient.newHttpClient()
                .send(request.build(), HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    private SpanData awaitSpan(String traceId) {
        await().atMost(Duration.ofSeconds(10)).until(() -> serverSpan(traceId) != null);
        return serverSpan(traceId);
    }

    private SpanData serverSpan(String traceId) {
        return exported.spans.stream()
                .filter(s -> s.getTraceId().equals(traceId) && s.getKind() == SpanKind.SERVER)
                .findFirst()
                .orElse(null);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExporterConfiguration {

        @Bean
        CollectingSpanExporter collectingSpanExporter() {
            return new CollectingSpanExporter();
        }
    }

    /** Keeps exported spans in memory instead of sending them anywhere. */
    static class CollectingSpanExporter implements SpanExporter {

        final List<SpanData> spans = new CopyOnWriteArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> batch) {
            spans.addAll(batch);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
