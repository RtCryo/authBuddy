package com.homebuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwksEndpointTests extends IntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired TokenService tokens;

    @Test
    void publishesCurrentAndPreviousPublicKeysOnly() throws Exception {
        HttpResponse<String> response = get("/.well-known/jwks.json");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/json");
        JWKSet set = JWKSet.parse(response.body());
        assertThat(set.getKeys()).hasSize(2);
        assertThat(set.getKeys()).allSatisfy(key -> assertThat(key.isPrivate()).isFalse());
        assertThat(set.getKeys()).allSatisfy(key -> assertThat(key.getKeyID()).isNotBlank());
        assertThat(response.body()).doesNotContain("\"d\"").doesNotContain("\"p\"");
    }

    @Test
    void verifiesAFreshTokenUsingOnlyTheJwks() throws Exception {
        UUID accountId = UUID.randomUUID();
        String token = tokens.issue(accountId, "family@example.com");

        JWKSet set = JWKSet.parse(get("/.well-known/jwks.json").body());
        SignedJWT jwt = SignedJWT.parse(token);
        JWK key = set.getKeyByKeyId(jwt.getHeader().getKeyID());

        assertThat(key).isNotNull();
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(jwt.verify(new RSASSAVerifier((RSAKey) key))).isTrue();

        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("homebuddy-auth");
        assertThat(claims.getSubject()).isEqualTo(accountId.toString());
        assertThat(claims.getStringClaim("email")).isEqualTo("family@example.com");
        assertThat(claims.getAudience()).containsExactly("finanzbuddy", "calendarbuddy");
        assertThat(claims.getExpirationTime().getTime() - claims.getIssueTime().getTime())
                .isEqualTo(86_400_000L);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }
}
