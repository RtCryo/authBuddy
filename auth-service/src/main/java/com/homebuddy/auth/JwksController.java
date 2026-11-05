package com.homebuddy.auth;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publishes the public signing keys; private key material is never included. */
@RestController
@ServerMode
class JwksController {

    private final SigningKeys keys;

    JwksController(SigningKeys keys) {
        this.keys = keys;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> jwks() {
        return keys.publicKeys().toJSONObject(true);
    }
}
