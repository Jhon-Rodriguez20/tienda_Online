package com.fesc.tiendaOnline.service;

import com.fesc.tiendaOnline.model.dto.CompraResponseDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Almacén de idempotencia respaldado por un Cache de Caffeine.
 * Almacena el par {claveIdempotencia → CompraResponseDTO} con TTL de 24 horas
 * para evitar la re-ejecución de operaciones de compra duplicadas.
 *
 * Validates: Requirements 1.2, 1.3, 1.6, 2.2, 2.3
 */
@Component
public class IdempotencyStore {

    private final Cache<String, CompraResponseDTO> store;

    public IdempotencyStore(
            @Value("${idempotency.ttl-hours:24}") long ttlHours,
            @Value("${idempotency.max-size:10000}") long maxSize) {
        this.store = Caffeine.newBuilder()
                .expireAfterWrite(ttlHours, TimeUnit.HOURS)
                .maximumSize(maxSize)
                .build();
    }

    /**
     * Recupera la respuesta almacenada para la clave dada.
     *
     * @param key clave de idempotencia (UUID v4)
     * @return Optional con el CompraResponseDTO si existe y no ha expirado, vacío en caso contrario
     */
    public Optional<CompraResponseDTO> get(String key) {
        return Optional.ofNullable(store.getIfPresent(key));
    }

    /**
     * Almacena la respuesta asociada a la clave de idempotencia.
     *
     * @param key      clave de idempotencia (UUID v4)
     * @param response respuesta a almacenar
     */
    public void put(String key, CompraResponseDTO response) {
        store.put(key, response);
    }

    /**
     * Verifica si existe una entrada para la clave dada (y no ha expirado).
     *
     * @param key clave de idempotencia (UUID v4)
     * @return true si la clave existe en el store, false en caso contrario
     */
    public boolean contains(String key) {
        return store.getIfPresent(key) != null;
    }
}
