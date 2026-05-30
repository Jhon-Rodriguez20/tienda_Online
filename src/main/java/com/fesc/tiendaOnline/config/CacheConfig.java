package com.fesc.tiendaOnline.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    // Región: productos
    @Value("${cache.productos.ttl-minutes:10}")
    private long productosTtlMinutes;

    @Value("${cache.productos.max-size:100}")
    private long productosMaxSize;

    // Región: busquedaProductos
    @Value("${cache.busqueda-productos.ttl-minutes:5}")
    private long busquedaTtlMinutes;

    @Value("${cache.busqueda-productos.max-size:200}")
    private long busquedaMaxSize;

    // Región: productoPorId
    @Value("${cache.producto-por-id.ttl-minutes:10}")
    private long productoPorIdTtlMinutes;

    @Value("${cache.producto-por-id.max-size:500}")
    private long productoPorIdMaxSize;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // Región "productos": TTL=10min, maxSize=100
        manager.registerCustomCache("productos",
                Caffeine.newBuilder()
                        .expireAfterWrite(productosTtlMinutes, TimeUnit.MINUTES)
                        .maximumSize(productosMaxSize)
                        .build());

        // Región "busquedaProductos": TTL=5min, maxSize=200
        manager.registerCustomCache("busquedaProductos",
                Caffeine.newBuilder()
                        .expireAfterWrite(busquedaTtlMinutes, TimeUnit.MINUTES)
                        .maximumSize(busquedaMaxSize)
                        .build());

        // Región "productoPorId": TTL=10min, maxSize=500
        manager.registerCustomCache("productoPorId",
                Caffeine.newBuilder()
                        .expireAfterWrite(productoPorIdTtlMinutes, TimeUnit.MINUTES)
                        .maximumSize(productoPorIdMaxSize)
                        .build());

        return manager;
    }
}
