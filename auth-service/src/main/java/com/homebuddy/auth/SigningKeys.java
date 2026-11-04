package com.homebuddy.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * The current signing key plus the public keys that stay published for verification.
 *
 * <p>Key ids are RFC 7638 SHA-256 thumbprints, so they stay stable across restarts. Error messages
 * never contain key material.
 */
public final class SigningKeys {

    private static final int MIN_MODULUS_BITS = 2048;

    private final RSAKey signingKey;
    private final JWKSet publicKeys;

    private SigningKeys(RSAKey signingKey, JWKSet publicKeys) {
        this.signingKey = signingKey;
        this.publicKeys = publicKeys;
    }

    /** Loads the keys, failing when no valid signing key is configured. */
    public static SigningKeys from(AuthProperties properties) {
        RSAKey current = loadCurrent(properties);
        List<JWK> published = new ArrayList<>();
        published.add(current.toPublicJWK());
        if (properties.previousPublicKeys() != null) {
            for (String pem : properties.previousPublicKeys()) {
                if (pem == null || pem.isBlank()) {
                    continue;
                }
                RSAKey previous = toPublicJwk(parsePublicKey(pem));
                boolean duplicate =
                        published.stream()
                                .anyMatch(
                                        key -> Objects.equals(key.getKeyID(), previous.getKeyID()));
                if (!duplicate) {
                    published.add(previous);
                }
            }
        }
        return new SigningKeys(current, new JWKSet(published));
    }

    /** The current key including its private part, for signing. */
    public RSAKey signingKey() {
        return signingKey;
    }

    /** The public keys to publish: the current key first, then the previous ones. */
    public JWKSet publicKeys() {
        return publicKeys;
    }

    private static RSAKey loadCurrent(AuthProperties properties) {
        String pem = properties.signingKey();
        if ((pem == null || pem.isBlank())
                && properties.signingKeyFile() != null
                && !properties.signingKeyFile().isBlank()) {
            try {
                pem =
                        Files.readString(
                                Path.of(properties.signingKeyFile()), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("The signing key file cannot be read", e);
            }
        }
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException(
                    "No signing key configured: set AUTH_SIGNING_KEY or AUTH_SIGNING_KEY_FILE");
        }
        RSAPrivateCrtKey privateKey = parsePrivateKey(pem);
        RSAPublicKey publicKey = derivePublicKey(privateKey);
        try {
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException("The signing key id cannot be computed", e);
        }
    }

    private static RSAPrivateCrtKey parsePrivateKey(String pem) {
        byte[] der = decodePem(pem, "PRIVATE KEY", "a PKCS#8 private key (BEGIN PRIVATE KEY)");
        try {
            var key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(key instanceof RSAPrivateCrtKey crt)) {
                throw new IllegalStateException("The signing key must be an RSA private key");
            }
            requireStrength(crt.getModulus());
            return crt;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("The signing key is not a valid RSA private key", e);
        }
    }

    private static RSAPublicKey parsePublicKey(String pem) {
        byte[] der = decodePem(pem, "PUBLIC KEY", "an X.509 public key (BEGIN PUBLIC KEY)");
        try {
            var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            RSAPublicKey rsa = (RSAPublicKey) key;
            requireStrength(rsa.getModulus());
            return rsa;
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException("A previous public key is not a valid RSA key", e);
        }
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateCrtKey privateKey) {
        try {
            return (RSAPublicKey)
                    KeyFactory.getInstance("RSA")
                            .generatePublic(
                                    new RSAPublicKeySpec(
                                            privateKey.getModulus(),
                                            privateKey.getPublicExponent()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("The public key cannot be derived", e);
        }
    }

    private static RSAKey toPublicJwk(RSAPublicKey publicKey) {
        try {
            return new RSAKey.Builder(publicKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException("A previous key id cannot be computed", e);
        }
    }

    private static void requireStrength(BigInteger modulus) {
        if (modulus.bitLength() < MIN_MODULUS_BITS) {
            throw new IllegalStateException("RSA keys must be at least 2048 bits");
        }
    }

    private static byte[] decodePem(String pem, String label, String expected) {
        String normalized = pem.replace("\\n", "\n").trim();
        String begin = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";
        if (!normalized.startsWith(begin) || !normalized.endsWith(end)) {
            throw new IllegalStateException("Expected " + expected);
        }
        String body =
                normalized.substring(begin.length(), normalized.length() - end.length()).trim();
        try {
            return Base64.getMimeDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("The PEM body is not valid Base64", e);
        }
    }
}
