package com.homebuddy.auth;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Loads the signing keys at startup; the application does not start without a valid key. */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
class KeyConfiguration {

    @Bean
    SigningKeys signingKeys(AuthProperties properties) {
        return SigningKeys.from(properties);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
