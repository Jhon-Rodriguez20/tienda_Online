package com.fesc.tiendaOnline.component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class JwtBlacklist {

    private final Cache<String, Boolean> blacklist;

    public JwtBlacklist(
            @Value("${jwt.blacklist.max-size:100000}") long maxSize) {
        this.blacklist = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .build();
    }

    public void add(String jti) {
        blacklist.put(jti, Boolean.TRUE);
    }

    public boolean isBlacklisted(String jti) {
        return blacklist.getIfPresent(jti) != null;
    }
}
