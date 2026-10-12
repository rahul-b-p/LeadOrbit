package com.leadorbit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.local")
public record StorageProperties(String basePath) {

    public StorageProperties {
        if (basePath == null || basePath.isBlank()) {
            basePath = "./uploads";
        }
    }
}