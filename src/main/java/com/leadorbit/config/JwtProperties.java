package com.leadorbit.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
    String accessSecret,
    Duration accessExpiry,
    String refreshSecret,
    Duration refreshExpiry) {
}
