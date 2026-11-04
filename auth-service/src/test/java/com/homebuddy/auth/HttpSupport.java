package com.homebuddy.auth;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/** Minimal HTTP client for integration tests against the random server port. */
final class HttpSupport {

    /** Status, raw body, and the first value of the Retry-After header. */
    record Reply(int status, String body, String retryAfter) {

        Map<String, Object> json() throws Exception {
            return JSONObjectUtils.parse(body);
        }
    }

    private HttpSupport() {}

    static Reply post(int port, String path, String json) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        return new Reply(
                response.statusCode(),
                response.body(),
                response.headers().firstValue("Retry-After").orElse(null));
    }

    static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
