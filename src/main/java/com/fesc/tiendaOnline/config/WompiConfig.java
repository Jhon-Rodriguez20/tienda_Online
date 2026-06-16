package com.fesc.tiendaOnline.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class WompiConfig {

    @Value("${wompi.public-key}")
    private String publicKey;

    @Value("${wompi.private-key}")
    private String privateKey;

    @Value("${wompi.events-key}")
    private String eventsKey;

    @Value("${wompi.integrity-key}")
    private String integrityKey;

    @Value("${wompi.base-url}")
    private String baseUrl;

    @PostConstruct
    public void validate() {
        if (isBlank(publicKey) || isBlank(privateKey)
                || isBlank(eventsKey) || isBlank(integrityKey)) {
            throw new IllegalStateException("Las credenciales de Wompi no están configuradas");
        }
    }

    // Helpers
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
