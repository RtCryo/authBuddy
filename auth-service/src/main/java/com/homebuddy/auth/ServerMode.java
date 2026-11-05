package com.homebuddy.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Marks components that exist only when the service runs as the HTTP server. The operator-only
 * {@code import-legacy} command sets {@code auth.mode=import-legacy}, so it starts without a web
 * server, endpoints, or signing keys.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(name = "auth.mode", havingValue = "server", matchIfMissing = true)
public @interface ServerMode {}
