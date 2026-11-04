package com.homebuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SigningKeysTests {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(KeyConfiguration.class);

    @Test
    void rotationKeepsOldAndNewTokensVerifiable() throws Exception {
        TestKeys.Pem oldKey = TestKeys.generate();
        TestKeys.Pem newKey = TestKeys.generate();
        UUID account = UUID.randomUUID();

        String oldToken = issuer(oldKey.privateKey()).issue(account, "a@example.com");
        // Rotation: the new key becomes current and the old public key stays published.
        SigningKeys rotated =
                SigningKeys.from(TestKeys.properties(newKey.privateKey(), oldKey.publicKey()));
        String newToken =
                new TokenService(
                                rotated,
                                TestKeys.properties(newKey.privateKey()),
                                Clock.systemUTC())
                        .issue(account, "a@example.com");

        JWKSet published = rotated.publicKeys();
        assertThat(published.getKeys()).hasSize(2);
        assertThat(verifies(oldToken, published)).isTrue();
        assertThat(verifies(newToken, published)).isTrue();
        assertThat(SignedJWT.parse(oldToken).getHeader().getKeyID())
                .isNotEqualTo(SignedJWT.parse(newToken).getHeader().getKeyID());

        // After the old key is removed, only the new tokens verify.
        SigningKeys afterRemoval = SigningKeys.from(TestKeys.properties(newKey.privateKey()));
        assertThat(verifies(oldToken, afterRemoval.publicKeys())).isFalse();
        assertThat(verifies(newToken, afterRemoval.publicKeys())).isTrue();
    }

    @Test
    void keyIdIsStableAcrossRestarts() {
        TestKeys.Pem key = TestKeys.generate();

        String first =
                SigningKeys.from(TestKeys.properties(key.privateKey())).signingKey().getKeyID();
        String second =
                SigningKeys.from(TestKeys.properties(key.privateKey())).signingKey().getKeyID();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void loadsTheKeyFromAFileAndFromEscapedNewlines(@TempDir Path dir) throws Exception {
        TestKeys.Pem key = TestKeys.generate();
        Path file = Files.writeString(dir.resolve("signing.pem"), key.privateKey());

        SigningKeys fromFile =
                SigningKeys.from(
                        new AuthProperties(
                                "",
                                file.toString(),
                                java.util.List.of(),
                                "homebuddy-auth",
                                java.util.List.of("finanzbuddy"),
                                1000));
        SigningKeys inline =
                SigningKeys.from(TestKeys.properties(key.privateKey().replace("\n", "\\n")));

        assertThat(fromFile.signingKey().getKeyID()).isEqualTo(inline.signingKey().getKeyID());
    }

    @Test
    void startupFailsWithoutASigningKey() {
        runner.withPropertyValues("auth.signing-key=")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .rootCause()
                                    .hasMessageContaining("No signing key configured");
                        });
    }

    @Test
    void startupFailsWithAnInvalidOrWeakKey() {
        runner.withPropertyValues("auth.signing-key=not-a-key")
                .run(context -> assertThat(context).hasFailed());

        String weak = TestKeys.generate(1024).privateKey().replace("\n", "\\n");
        runner.withPropertyValues("auth.signing-key=" + weak)
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .rootCause()
                                    .hasMessageContaining("at least 2048 bits");
                        });
    }

    @Test
    void startupSucceedsWithAValidKey() {
        String valid = TestKeys.generate().privateKey().replace("\n", "\\n");

        runner.withPropertyValues("auth.signing-key=" + valid)
                .run(context -> assertThat(context).hasSingleBean(SigningKeys.class));
    }

    private static TokenService issuer(String privateKey) {
        return new TokenService(
                SigningKeys.from(TestKeys.properties(privateKey)),
                TestKeys.properties(privateKey),
                Clock.systemUTC());
    }

    private static boolean verifies(String token, JWKSet keys) throws Exception {
        SignedJWT jwt = SignedJWT.parse(token);
        var key = keys.getKeyByKeyId(jwt.getHeader().getKeyID());
        return key != null && jwt.verify(new RSASSAVerifier((RSAKey) key));
    }
}
