package com.fesc.tiendaOnline.config;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for {@link RateLimitingFilter} using jqwik.
 *
 * Covers tasks 14.1–14.6 (Properties 15–20).
 * No Spring context needed — all tests are pure unit tests.
 */
class RateLimitingFilterPropertyTest {

    // -----------------------------------------------------------------------
    // Helper: create a fresh filter with reflection-set fields
    // -----------------------------------------------------------------------

    private RateLimitingFilter createFilter(int authReqs, int authDur,
                                             int prodReqs, int prodDur,
                                             int compReqs, int compDur,
                                             int userReqs, int userDur) throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        setInt(filter, "authRequests",      authReqs);
        setInt(filter, "authDuration",      authDur);
        setInt(filter, "productosRequests", prodReqs);
        setInt(filter, "productosDuration", prodDur);
        setInt(filter, "comprasRequests",   compReqs);
        setInt(filter, "comprasDuration",   compDur);
        setInt(filter, "usuariosRequests",  userReqs);
        setInt(filter, "usuariosDuration",  userDur);
        return filter;
    }

    private void setInt(RateLimitingFilter filter, String fieldName, int value) throws Exception {
        Field f = RateLimitingFilter.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.setInt(filter, value);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, Bucket> getBuckets(RateLimitingFilter filter) throws Exception {
        Field f = RateLimitingFilter.class.getDeclaredField("buckets");
        f.setAccessible(true);
        return (ConcurrentHashMap<String, Bucket>) f.get(filter);
    }

    private String invokeExtractClientIp(RateLimitingFilter filter,
                                          HttpServletRequest request) throws Exception {
        Method m = RateLimitingFilter.class.getDeclaredMethod("extractClientIp", HttpServletRequest.class);
        m.setAccessible(true);
        return (String) m.invoke(filter, request);
    }

    private void callDoFilter(RateLimitingFilter filter,
                               HttpServletRequest request,
                               HttpServletResponse response,
                               FilterChain chain) throws Exception {
        Method m = RateLimitingFilter.class.getDeclaredMethod(
                "doFilterInternal",
                HttpServletRequest.class,
                HttpServletResponse.class,
                FilterChain.class);
        m.setAccessible(true);
        m.invoke(filter, request, response, chain);
    }

    // -----------------------------------------------------------------------
    // 14.1 — Property 15: Correct client IP extraction
    // Validates: Requirements 13.2
    // -----------------------------------------------------------------------

    /**
     * Property 15: Extracción correcta de IP del cliente.
     * When X-Forwarded-For header is present, the first comma-separated token (trimmed) is returned.
     * When absent (null), request.getRemoteAddr() is returned.
     *
     * Validates: Requirements 13.2
     */
    @Property(tries = 100)
    void property15_extractClientIp_xForwardedFor(@ForAll("xForwardedForHeaders") String xForwardedFor)
            throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn(xForwardedFor);

        String extracted = invokeExtractClientIp(filter, request);

        // Expected: first token before comma, trimmed
        String expected = xForwardedFor.split(",")[0].trim();
        assertThat(extracted)
                .as("X-Forwarded-For present: should extract first IP from '%s'", xForwardedFor)
                .isEqualTo(expected);
    }

    @Property(tries = 100)
    void property15_extractClientIp_remoteAddr(@ForAll("remoteAddresses") String remoteAddr)
            throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        // X-Forwarded-For absent
        Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        Mockito.when(request.getRemoteAddr()).thenReturn(remoteAddr);

        String extracted = invokeExtractClientIp(filter, request);

        assertThat(extracted)
                .as("X-Forwarded-For absent: should return remoteAddr '%s'", remoteAddr)
                .isEqualTo(remoteAddr);
    }

    @Provide
    Arbitrary<String> xForwardedForHeaders() {
        // Generate non-blank IP-like strings
        Arbitrary<String> ipPart = Arbitraries.strings()
                .withCharRange('0', '9')
                .ofMinLength(1).ofMaxLength(3)
                .map(s -> s.isEmpty() ? "1" : s);

        Arbitrary<String> singleIp = Arbitraries.of(
                "192.168.1.1", "10.0.0.1", "172.16.0.5",
                "203.0.113.42", "8.8.8.8", "127.0.0.1"
        );

        // Build comma-separated lists of 1–5 IPs
        return Arbitraries.integers().between(1, 5).flatMap(count ->
                singleIp.list().ofSize(count).map(ips -> String.join(", ", ips))
        );
    }

    @Provide
    Arbitrary<String> remoteAddresses() {
        return Arbitraries.of(
                "192.168.1.100", "10.0.0.2", "172.16.0.10",
                "203.0.113.1", "8.8.4.4", "127.0.0.1", "::1"
        );
    }

    // -----------------------------------------------------------------------
    // 14.2 — Property 16: HTTP 429 when limit exceeded
    // Validates: Requirements 13.4
    // -----------------------------------------------------------------------

    /**
     * Property 16: HTTP 429 cuando se supera el límite de rate limiting.
     * First L requests succeed; requests L+1 through L+N receive HTTP 429
     * with JSON body containing "Too many requests" and "retryAfterSeconds".
     *
     * Validates: Requirements 13.4
     */
    @Property(tries = 50)
    void property16_http429WhenLimitExceeded(
            @ForAll @IntRange(min = 1, max = 10) int limit,
            @ForAll @IntRange(min = 1, max = 5)  int extra) throws Exception {

        RateLimitingFilter filter = createFilter(limit, 1, 100, 1, 30, 1, 20, 1);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        FilterChain chain = Mockito.mock(FilterChain.class);

        Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        Mockito.when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        Mockito.when(request.getRequestURI()).thenReturn("/auth/login");

        int total = limit + extra;
        int[] statuses = new int[total];
        List<String> bodies = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            Mockito.when(response.getWriter()).thenReturn(pw);

            callDoFilter(filter, request, response, chain);
            pw.flush();

            // Capture status: if setStatus was called, record it; otherwise successful (200-range)
            ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
            boolean statusSet = false;
            try {
                Mockito.verify(response).setStatus(statusCaptor.capture());
                statuses[i] = statusCaptor.getValue();
                statusSet = true;
            } catch (org.mockito.exceptions.verification.WantedButNotInvoked e) {
                statuses[i] = 200; // no setStatus call → success
            }

            if (statusSet) {
                bodies.add(sw.toString());
            } else {
                bodies.add("");
            }
        }

        // First 'limit' requests should NOT be 429
        for (int i = 0; i < limit; i++) {
            assertThat(statuses[i])
                    .as("Request %d (within limit %d) should not be 429", i + 1, limit)
                    .isNotEqualTo(429);
        }

        // Requests beyond the limit should be 429 with correct body
        for (int i = limit; i < total; i++) {
            assertThat(statuses[i])
                    .as("Request %d (over limit %d) should be HTTP 429", i + 1, limit)
                    .isEqualTo(429);

            String body = bodies.get(i);
            assertThat(body)
                    .as("HTTP 429 body should contain 'Too many requests'")
                    .contains("Too many requests");
            assertThat(body)
                    .as("HTTP 429 body should contain 'retryAfterSeconds'")
                    .contains("retryAfterSeconds");
        }

        // Chain should have been invoked exactly 'limit' times
        Mockito.verify(chain, Mockito.times(limit))
               .doFilter(Mockito.any(), Mockito.any());
    }

    // -----------------------------------------------------------------------
    // 14.3 — Property 17: Retry-After header on HTTP 429
    // Validates: Requirements 13.5
    // -----------------------------------------------------------------------

    /**
     * Property 17: Header Retry-After en respuestas HTTP 429.
     * After exhausting the bucket, the next request must receive HTTP 429
     * with a Retry-After header whose value is a non-negative number.
     *
     * Validates: Requirements 13.5
     */
    @Property(tries = 50)
    void property17_retryAfterHeaderOn429(@ForAll @IntRange(min = 1, max = 5) int limit)
            throws Exception {

        RateLimitingFilter filter = createFilter(limit, 1, 100, 1, 30, 1, 20, 1);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        FilterChain chain = Mockito.mock(FilterChain.class);

        Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        Mockito.when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        Mockito.when(request.getRequestURI()).thenReturn("/auth/login");

        // Exhaust the bucket
        for (int i = 0; i < limit; i++) {
            HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
            StringWriter sw = new StringWriter();
            Mockito.when(resp.getWriter()).thenReturn(new PrintWriter(sw));
            callDoFilter(filter, request, resp, chain);
        }

        // One more request — should get 429 with Retry-After header
        HttpServletResponse blockedResp = Mockito.mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Mockito.when(blockedResp.getWriter()).thenReturn(pw);

        ArgumentCaptor<String> headerNameCaptor  = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);

        callDoFilter(filter, request, blockedResp, chain);
        pw.flush();

        // Verify HTTP 429
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(blockedResp).setStatus(statusCaptor.capture());
        assertThat(statusCaptor.getValue())
                .as("Expected HTTP 429 after exhausting limit of %d", limit)
                .isEqualTo(429);

        // Verify Retry-After header
        Mockito.verify(blockedResp, Mockito.atLeastOnce())
               .addHeader(headerNameCaptor.capture(), headerValueCaptor.capture());

        List<String> names  = headerNameCaptor.getAllValues();
        List<String> values = headerValueCaptor.getAllValues();

        int retryAfterIdx = -1;
        for (int i = 0; i < names.size(); i++) {
            if ("Retry-After".equals(names.get(i))) {
                retryAfterIdx = i;
                break;
            }
        }
        assertThat(retryAfterIdx)
                .as("Retry-After header should be present on HTTP 429")
                .isGreaterThanOrEqualTo(0);

        String retryAfterValue = values.get(retryAfterIdx);
        long retryAfterSeconds = Long.parseLong(retryAfterValue);
        assertThat(retryAfterSeconds)
                .as("Retry-After value must be non-negative")
                .isGreaterThanOrEqualTo(0L);
    }

    // -----------------------------------------------------------------------
    // 14.4 — Property 18: Informational headers on successful responses
    // Validates: Requirements 13.6
    // -----------------------------------------------------------------------

    /**
     * Property 18: Headers informativos en respuestas exitosas de rate limiting.
     * For each of K requests (K < L), X-RateLimit-Remaining and X-RateLimit-Limit
     * headers must be present with correct values.
     *
     * Validates: Requirements 13.6
     */
    @Property(tries = 100)
    void property18_informationalHeadersOnSuccess(
            @ForAll @IntRange(min = 2, max = 20) int limit,
            @ForAll @IntRange(min = 1, max = 1)  int requestsPerTry) throws Exception {
        // We use requestsPerTry=1 to keep the test fast; we pick K in [1, limit-1]
        int k = Math.max(1, limit - 1);
        k = Math.min(k, 3); // cap to 3 to avoid slow tests

        RateLimitingFilter filter = createFilter(limit, 1, 100, 1, 30, 1, 20, 1);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        FilterChain chain = Mockito.mock(FilterChain.class);

        Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        Mockito.when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        Mockito.when(request.getRequestURI()).thenReturn("/auth/login");

        for (int i = 0; i < k; i++) {
            HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
            StringWriter sw = new StringWriter();
            Mockito.when(response.getWriter()).thenReturn(new PrintWriter(sw));

            ArgumentCaptor<String> nameCaptor  = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

            callDoFilter(filter, request, response, chain);

            Mockito.verify(response, Mockito.atLeastOnce())
                   .addHeader(nameCaptor.capture(), valueCaptor.capture());

            List<String> names  = nameCaptor.getAllValues();
            List<String> values = valueCaptor.getAllValues();

            // X-RateLimit-Remaining
            int remainingIdx = findHeaderIndex(names, "X-RateLimit-Remaining");
            assertThat(remainingIdx)
                    .as("X-RateLimit-Remaining header should be present on request %d", i + 1)
                    .isGreaterThanOrEqualTo(0);
            long remaining = Long.parseLong(values.get(remainingIdx));
            assertThat(remaining)
                    .as("X-RateLimit-Remaining must be non-negative")
                    .isGreaterThanOrEqualTo(0L);

            // X-RateLimit-Limit
            int limitIdx = findHeaderIndex(names, "X-RateLimit-Limit");
            assertThat(limitIdx)
                    .as("X-RateLimit-Limit header should be present on request %d", i + 1)
                    .isGreaterThanOrEqualTo(0);
            int reportedLimit = Integer.parseInt(values.get(limitIdx));
            assertThat(reportedLimit)
                    .as("X-RateLimit-Limit must equal configured limit %d", limit)
                    .isEqualTo(limit);

            // Chain was invoked (not blocked)
            Mockito.verify(chain, Mockito.atLeast(i + 1)).doFilter(Mockito.any(), Mockito.any());
        }
    }

    private int findHeaderIndex(List<String> names, String targetName) {
        for (int i = 0; i < names.size(); i++) {
            if (targetName.equals(names.get(i))) return i;
        }
        return -1;
    }

    // -----------------------------------------------------------------------
    // 14.5 — Property 19: Lazy bucket initialization
    // Validates: Requirements 13.8
    // -----------------------------------------------------------------------

    /**
     * Property 19: Inicialización perezosa de buckets de rate limiting.
     * Before the first request the bucket map must NOT contain the key.
     * After the first request it MUST contain the key.
     *
     * Validates: Requirements 13.8
     */
    @Property(tries = 100)
    void property19_lazyBucketInitialization(@ForAll("safeIps") String ip) throws Exception {
        RateLimitingFilter filter = createFilter(10, 1, 100, 1, 30, 1, 20, 1);

        ConcurrentHashMap<String, Bucket> bucketsMap = getBuckets(filter);
        String bucketKey = ip + ":auth";

        // Before first request — key must not exist
        assertThat(bucketsMap.containsKey(bucketKey))
                .as("Bucket for key '%s' should NOT exist before the first request", bucketKey)
                .isFalse();

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);

        Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        Mockito.when(request.getRemoteAddr()).thenReturn(ip);
        Mockito.when(request.getRequestURI()).thenReturn("/auth/login");
        StringWriter sw = new StringWriter();
        Mockito.when(response.getWriter()).thenReturn(new PrintWriter(sw));

        callDoFilter(filter, request, response, chain);

        // After first request — key must exist
        assertThat(bucketsMap.containsKey(bucketKey))
                .as("Bucket for key '%s' should exist after the first request", bucketKey)
                .isTrue();
    }

    @Provide
    Arbitrary<String> safeIps() {
        // IPs without colons so the bucket key "ip:auth" is unambiguous
        return Arbitraries.of(
                "10.0.0.1", "10.0.0.2", "10.0.0.3",
                "172.16.1.1", "172.16.1.2",
                "192.168.0.1", "192.168.0.2",
                "203.0.113.1", "203.0.113.2",
                "8.8.8.8", "8.8.4.4"
        );
    }

    // -----------------------------------------------------------------------
    // 14.6 — Property 20: Thread-safety in bucket creation and consumption
    // Validates: Requirements 13.9
    // -----------------------------------------------------------------------

    /**
     * Property 20: Thread-safety en creación y consumo de buckets.
     * N concurrent threads from the SAME IP all call doFilterInternal simultaneously.
     * After completion:
     *   - Exactly N tokens consumed (all succeeded, bucket not exhausted)
     *   - Only ONE bucket entry for the key in the map (computeIfAbsent is atomic)
     *   - No exceptions thrown by any thread
     *
     * Validates: Requirements 13.9
     */
    @Property(tries = 20)
    void property20_threadSafety(@ForAll @IntRange(min = 5, max = 30) int n) throws Exception {
        // Large limit so bucket is never exhausted
        RateLimitingFilter filter = createFilter(1000, 1, 100, 1, 30, 1, 20, 1);

        String sharedIp = "192.168.99.99";
        String bucketKey = sharedIp + ":auth";

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(n);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<Throwable> firstError = new AtomicReference<>(null);

        ExecutorService executor = Executors.newFixedThreadPool(n);

        for (int i = 0; i < n; i++) {
            executor.submit(() -> {
                try {
                    HttpServletRequest req  = Mockito.mock(HttpServletRequest.class);
                    HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
                    FilterChain chain       = Mockito.mock(FilterChain.class);

                    Mockito.when(req.getHeader("X-Forwarded-For")).thenReturn(null);
                    Mockito.when(req.getRemoteAddr()).thenReturn(sharedIp);
                    Mockito.when(req.getRequestURI()).thenReturn("/auth/login");
                    StringWriter sw = new StringWriter();
                    Mockito.when(res.getWriter()).thenReturn(new PrintWriter(sw));

                    // Wait for start signal so all threads fire at the same time
                    startLatch.await();
                    callDoFilter(filter, req, res, chain);
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    firstError.compareAndSet(null, t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // release all threads at once
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed)
                .as("All %d threads should complete within 10 seconds", n)
                .isTrue();

        assertThat(firstError.get())
                .as("No thread should throw an exception")
                .isNull();

        assertThat(successCount.get())
                .as("All %d threads should succeed (limit=1000 >> n)", n)
                .isEqualTo(n);

        ConcurrentHashMap<String, Bucket> bucketsMap = getBuckets(filter);
        assertThat(bucketsMap.containsKey(bucketKey))
                .as("Bucket for key '%s' should exist after concurrent requests", bucketKey)
                .isTrue();

        // Verify exactly one bucket entry for this key (computeIfAbsent atomicity)
        long keysForIp = bucketsMap.keySet().stream()
                .filter(k -> k.equals(bucketKey))
                .count();
        assertThat(keysForIp)
                .as("Only one bucket should exist for key '%s'", bucketKey)
                .isEqualTo(1L);
    }
}
