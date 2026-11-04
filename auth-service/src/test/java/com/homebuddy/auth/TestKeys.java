package com.homebuddy.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Generates throwaway RSA keys in PEM form; no key material is stored in the repository. */
final class TestKeys {

    /** A generated key pair in the PEM formats the service accepts. */
    record Pem(String privateKey, String publicKey) {}

    private TestKeys() {}

    static Pem generate() {
        return generate(2048);
    }

    static Pem generate(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            KeyPair pair = generator.generateKeyPair();
            return new Pem(
                    pem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                    pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static AuthProperties properties(String signingKey, String... previousPublicKeys) {
        return new AuthProperties(
                signingKey,
                null,
                java.util.List.of(previousPublicKeys),
                "homebuddy-auth",
                java.util.List.of("finanzbuddy", "calendarbuddy"),
                86_400_000L);
    }

    private static String pem(String label, byte[] der) {
        String body =
                Base64.getMimeEncoder(64, "\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                        .encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }
}
