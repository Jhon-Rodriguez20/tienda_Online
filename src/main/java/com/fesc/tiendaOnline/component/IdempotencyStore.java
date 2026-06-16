package com.fesc.tiendaOnline.component;

import com.fesc.tiendaOnline.model.dto.CompraResponseDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

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

    public Optional<CompraResponseDTO> get(String key) {
        return Optional.ofNullable(store.getIfPresent(key));
    }

    public void put(String key, CompraResponseDTO response) {
        store.put(key, response);
    }

    public boolean contains(String key) {
        return store.getIfPresent(key) != null;
    }
}
