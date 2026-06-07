package com.fesc.tiendaOnline.config;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Value("${rate-limit.auth.requests:10}")
    private int authRequests;

    @Value("${rate-limit.auth.duration-minutes:1}")
    private int authDuration;

    @Value("${rate-limit.productos.requests:100}")
    private int productosRequests;

    @Value("${rate-limit.productos.duration-minutes:1}")
    private int productosDuration;

    @Value("${rate-limit.compras.requests:30}")
    private int comprasRequests;

    @Value("${rate-limit.compras.duration-minutes:1}")
    private int comprasDuration;

    @Value("${rate-limit.usuarios.requests:20}")
    private int usuariosRequests;

    @Value("${rate-limit.usuarios.duration-minutes:1}")
    private int usuariosDuration;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIp = extractClientIp(request);
        String endpoint = determineEndpoint(request.getRequestURI());
        String bucketKey = clientIp + ":" + endpoint;

        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> createBucket(endpoint));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            response.addHeader("X-RateLimit-Limit", String.valueOf(getLimitForEndpoint(endpoint)));
            filterChain.doFilter(request, response);
        } else {
            long waitForRefill = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.addHeader("Retry-After", String.valueOf(waitForRefill));
            response.getWriter().write(String.format(
                    "{\"error\": \"Too many requests\", \"retryAfterSeconds\": %d}", waitForRefill));
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String determineEndpoint(String uri) {
        if (uri.startsWith("/auth/")) return "auth";
        if (uri.startsWith("/productos/")) return "productos";
        if (uri.startsWith("/compras/")) return "compras";
        if (uri.startsWith("/usuarios/")) return "usuarios";
        return "default";
    }

    private Bucket createBucket(String endpoint) {
        Bandwidth limit = switch (endpoint) {
            case "auth" -> Bandwidth.builder()
                    .capacity(authRequests)
                    .refillGreedy(authRequests, Duration.ofMinutes(authDuration))
                    .build();
            case "productos" -> Bandwidth.builder()
                    .capacity(productosRequests)
                    .refillGreedy(productosRequests, Duration.ofMinutes(productosDuration))
                    .build();
            case "compras" -> Bandwidth.builder()
                    .capacity(comprasRequests)
                    .refillGreedy(comprasRequests, Duration.ofMinutes(comprasDuration))
                    .build();
            case "usuarios" -> Bandwidth.builder()
                    .capacity(usuariosRequests)
                    .refillGreedy(usuariosRequests, Duration.ofMinutes(usuariosDuration))
                    .build();
            default -> Bandwidth.builder()
                    .capacity(100)
                    .refillGreedy(100, Duration.ofMinutes(1))
                    .build();
        };
        return Bucket.builder().addLimit(limit).build();
    }

    private int getLimitForEndpoint(String endpoint) {
        return switch (endpoint) {
            case "auth" -> authRequests;
            case "productos" -> productosRequests;
            case "compras" -> comprasRequests;
            case "usuarios" -> usuariosRequests;
            default -> 100;
        };
    }
}
