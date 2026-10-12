package com.leadorbit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.bootstrap.super-admin")
public record BootstrapAdminProperties(
    String email,
    String password,
    String name) {
}
