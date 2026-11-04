package com.homebuddy.auth;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Loads the signing keys at startup; the application does not start without a valid key. */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
class KeyConfiguration {

    @Bean
    SigningKeys signingKeys(AuthProperties properties) {
        return SigningKeys.from(properties);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
