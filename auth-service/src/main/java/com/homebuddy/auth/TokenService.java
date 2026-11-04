package com.homebuddy.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Issues homeBuddy account tokens as defined by the root {@code homebuddy-identity-contract}. */
@Service
public class TokenService {

    private final SigningKeys keys;
    private final AuthProperties properties;
    private final Clock clock;

    TokenService(SigningKeys keys, AuthProperties properties, Clock clock) {
        this.keys = keys;
        this.properties = properties;
        this.clock = clock;
    }

    /** Returns a signed RS256 JWT for the account. */
    public String issue(UUID accountId, String email) {
        Instant now = clock.instant();
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .issuer(properties.issuer())
                        .subject(accountId.toString())
                        .audience(properties.audiences())
                        .claim("email", email)
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusMillis(properties.tokenExpirationMs())))
                        .build();
        JWSHeader header =
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .keyID(keys.signingKey().getKeyID())
                        .build();
        try {
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(keys.signingKey()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("The token cannot be signed", e);
        }
    }
}
