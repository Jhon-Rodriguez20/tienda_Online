package com.fesc.tiendaOnline.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.exceptions.verification.WantedButNotInvoked;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RateLimitingFilter} — bucket isolation.
 *
 * Covers task 15.3 (Requirements 13.3, 13.4, 13.5, 13.7):
 *
 *   Test 1 — Two clients with different IPs, same endpoint: each has its own independent bucket.
 *   Test 2 — One client exhausting one endpoint's bucket does not affect other endpoints.
 *   Test 3 — After a bucket is evicted (simulating TTL expiry via reflection), requests succeed again.
 *
 * No Spring context needed — all tests are plain JUnit 5.
 */
class RateLimitingFilterIntegrationTest {

    // -----------------------------------------------------------------------
    // Reflection helpers (same pattern as RateLimitingFilterTest)
    // -----------------------------------------------------------------------

    private void setInt(RateLimitingFilter filter, String fieldName, int value) throws Exception {
        Field f = RateLimitingFilter.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.setInt(filter, value);
    }

    private void callDoFilter(RateLimitingFilter filter,
                               HttpServletRequest req,
                               HttpServletResponse res,
                               FilterChain chain) throws Exception {
        Method m = RateLimitingFilter.class.getDeclaredMethod(
                "doFilterInternal",
                HttpServletRequest.class,
                HttpServletResponse.class,
                FilterChain.class);
        m.setAccessible(true);
        m.invoke(filter, req, res, chain);
    }

    /** Access the private {@code buckets} ConcurrentHashMap field. */
    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, Object> getBuckets(RateLimitingFilter filter) throws Exception {
        Field f = RateLimitingFilter.class.getDeclaredField("buckets");
        f.setAccessible(true);
        return (ConcurrentHashMap<String, Object>) f.get(filter);
    }

    /** Build a mock request with the given URI and IP (via RemoteAddr, no X-Forwarded-For). */
    private HttpServletRequest buildRequest(String uri, String remoteAddr) {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getHeader("X-Forwarded-For")).thenReturn(null);
        Mockito.when(req.getRemoteAddr()).thenReturn(remoteAddr);
        Mockito.when(req.getRequestURI()).thenReturn(uri);
        return req;
    }

    /** Build a mock response with a fresh StringWriter / PrintWriter. */
    private HttpServletResponse buildResponse() throws Exception {
        HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        Mockito.when(res.getWriter()).thenReturn(new PrintWriter(sw));
        return res;
    }

    /**
     * Returns true when setStatus(429) was recorded on the response mock,
     * false when setStatus was never called (pass-through path).
     */
    private boolean wasBlocked(HttpServletResponse res) {
        ArgumentCaptor<Integer> cap = ArgumentCaptor.forClass(Integer.class);
        try {
            Mockito.verify(res).setStatus(cap.capture());
            return cap.getValue() == 429;
        } catch (WantedButNotInvoked ignored) {
            return false;
        }
    }

    /** Returns the value of the named header from the addHeader captor, or null if absent. */
    private String capturedHeader(HttpServletResponse res, String headerName) {
        ArgumentCaptor<String> nameCap  = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCap = ArgumentCaptor.forClass(String.class);
        try {
            Mockito.verify(res, Mockito.atLeastOnce()).addHeader(nameCap.capture(), valueCap.capture());
        } catch (WantedButNotInvoked ignored) {
            return null;
        }
        List<String> names  = nameCap.getAllValues();
        List<String> values = valueCap.getAllValues();
        for (int i = 0; i < names.size(); i++) {
            if (headerName.equals(names.get(i))) {
                return values.get(i);
            }
        }
        return null;
    }

    /** Create a filter with auth=authReqs/1min, productos=100/1min, default for rest. */
    private RateLimitingFilter createFilter(int authReqs) throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        setInt(filter, "authRequests",      authReqs);
        setInt(filter, "authDuration",      1);
        setInt(filter, "productosRequests", 100);
        setInt(filter, "productosDuration", 1);
        setInt(filter, "comprasRequests",   30);
        setInt(filter, "comprasDuration",   1);
        setInt(filter, "usuariosRequests",  20);
        setInt(filter, "usuariosDuration",  1);
        return filter;
    }

    // -----------------------------------------------------------------------
    // Test 1 — Two clients with different IPs, same endpoint → independent buckets
    // Validates: Requirements 13.3, 13.4, 13.7
    // -----------------------------------------------------------------------

    /**
     * Client A (IP 10.0.0.1) and Client B (IP 10.0.0.2) both hit /auth/login.
     * The auth limit is 5 per minute.
     *
     * Scenario:
     *   - Client A sends 5 requests → all succeed (bucket exhausted).
     *   - Client A sends 1 more request → HTTP 429.
     *   - Client B sends 1 request → must still succeed (its bucket is independent).
     *
     * This verifies that the bucket key is "{IP}:{endpoint}", not just "{endpoint}".
     */
    @Test
    void twoClientsWithDifferentIPs_haveIndependentBuckets() throws Exception {
        // auth limit = 5
        RateLimitingFilter filter = createFilter(5);
        FilterChain chain = Mockito.mock(FilterChain.class);

        HttpServletRequest reqA = buildRequest("/auth/login", "10.0.0.1");
        HttpServletRequest reqB = buildRequest("/auth/login", "10.0.0.2");

        // ── Client A: exhaust all 5 tokens ──────────────────────────────────
        for (int i = 0; i < 5; i++) {
            HttpServletResponse res = buildResponse();
            callDoFilter(filter, reqA, res, chain);
            assertThat(wasBlocked(res))
                    .as("Client A request %d/%d should succeed (within limit)", i + 1, 5)
                    .isFalse();
        }

        // ── Client A: 6th request is blocked ────────────────────────────────
        HttpServletResponse blockedA = buildResponse();
        callDoFilter(filter, reqA, blockedA, chain);
        assertThat(wasBlocked(blockedA))
                .as("Client A 6th request must be rate-limited (HTTP 429)")
                .isTrue();

        // ── Client B: still has a full bucket of 5 tokens ───────────────────
        HttpServletResponse resB = buildResponse();
        callDoFilter(filter, reqB, resB, chain);
        assertThat(wasBlocked(resB))
                .as("Client B first request must succeed — its bucket is independent from Client A's")
                .isFalse();

        // Filter chain must have been invoked exactly 5 (Client A) + 1 (Client B) = 6 times
        Mockito.verify(chain, Mockito.times(6)).doFilter(Mockito.any(), Mockito.any());
    }

    // -----------------------------------------------------------------------
    // Test 2 — Exhausting limit on one endpoint does NOT affect other endpoints
    // Validates: Requirements 13.3, 13.4, 13.7
    // -----------------------------------------------------------------------

    /**
     * A single client (IP 172.16.0.1) exhausts the /auth/ bucket (limit = 3).
     *
     * Scenario:
     *   - Client sends 3 requests to /auth/login → all succeed.
     *   - Client sends 1 more request to /auth/login → HTTP 429.
     *   - Client sends 1 request to /productos/list → must succeed (separate bucket key).
     *
     * This verifies that bucket keys are scoped to both IP and endpoint.
     */
    @Test
    void exhaustingOneEndpointDoesNotAffectOtherEndpoints() throws Exception {
        // auth limit = 3 (small so we can exhaust it quickly)
        RateLimitingFilter filter = new RateLimitingFilter();
        setInt(filter, "authRequests",      3);
        setInt(filter, "authDuration",      1);
        setInt(filter, "productosRequests", 100);
        setInt(filter, "productosDuration", 1);
        setInt(filter, "comprasRequests",   30);
        setInt(filter, "comprasDuration",   1);
        setInt(filter, "usuariosRequests",  20);
        setInt(filter, "usuariosDuration",  1);

        FilterChain chain = Mockito.mock(FilterChain.class);
        String clientIp = "172.16.0.1";

        HttpServletRequest authReq     = buildRequest("/auth/login",     clientIp);
        HttpServletRequest productosReq = buildRequest("/productos/list", clientIp);

        // ── Exhaust the /auth/ bucket (3 tokens) ────────────────────────────
        for (int i = 0; i < 3; i++) {
            HttpServletResponse res = buildResponse();
            callDoFilter(filter, authReq, res, chain);
            assertThat(wasBlocked(res))
                    .as("Auth request %d/%d should succeed", i + 1, 3)
                    .isFalse();
        }

        // ── Next /auth/ request must be blocked ─────────────────────────────
        HttpServletResponse blockedAuth = buildResponse();
        callDoFilter(filter, authReq, blockedAuth, chain);
        assertThat(wasBlocked(blockedAuth))
                .as("4th /auth/ request must return HTTP 429")
                .isTrue();

        // Verify Retry-After header is present on the 429 response
        String retryAfter = capturedHeader(blockedAuth, "Retry-After");
        assertThat(retryAfter)
                .as("HTTP 429 response must include the Retry-After header")
                .isNotNull();

        // ── Same client hitting /productos/ must still succeed ───────────────
        // Bucket key "172.16.0.1:productos" is completely independent from "172.16.0.1:auth"
        HttpServletResponse productosRes = buildResponse();
        callDoFilter(filter, productosReq, productosRes, chain);
        assertThat(wasBlocked(productosRes))
                .as("Request to /productos/ must succeed even though /auth/ limit is exhausted")
                .isFalse();

        // Filter chain: 3 (auth ok) + 1 (productos ok) = 4 invocations
        Mockito.verify(chain, Mockito.times(4)).doFilter(Mockito.any(), Mockito.any());
    }

    // -----------------------------------------------------------------------
    // Test 3 — After simulated TTL expiry (bucket removed), requests succeed again
    // Validates: Requirements 13.5
    // -----------------------------------------------------------------------

    /**
     * Simulates a client waiting for the Retry-After window to pass by directly
     * removing the exhausted bucket from the internal map (equivalent to TTL expiry).
     *
     * Scenario:
     *   - Filter has auth limit = 1.
     *   - Request #1 → succeeds (1 token consumed).
     *   - Request #2 → HTTP 429; Retry-After header is present.
     *   - Bucket entry removed from the map via reflection (simulating TTL/refill).
     *   - Request #3 → a new bucket is created lazily; succeeds; filter chain invoked.
     */
    @Test
    void afterRetryAfterExpiry_clientCanMakeRequestsAgain() throws Exception {
        // auth limit = 1 so the second request is immediately blocked
        RateLimitingFilter filter = new RateLimitingFilter();
        setInt(filter, "authRequests",      1);
        setInt(filter, "authDuration",      1);
        setInt(filter, "productosRequests", 100);
        setInt(filter, "productosDuration", 1);
        setInt(filter, "comprasRequests",   30);
        setInt(filter, "comprasDuration",   1);
        setInt(filter, "usuariosRequests",  20);
        setInt(filter, "usuariosDuration",  1);

        FilterChain chain = Mockito.mock(FilterChain.class);
        HttpServletRequest req = buildRequest("/auth/login", "192.168.50.1");

        // ── Request #1: consumes the only token ──────────────────────────────
        HttpServletResponse res1 = buildResponse();
        callDoFilter(filter, req, res1, chain);
        assertThat(wasBlocked(res1))
                .as("First request (within limit of 1) must succeed")
                .isFalse();

        // ── Request #2: bucket is empty → HTTP 429 with Retry-After ──────────
        HttpServletResponse res2 = buildResponse();
        callDoFilter(filter, req, res2, chain);
        assertThat(wasBlocked(res2))
                .as("Second request must be rate-limited (HTTP 429)")
                .isTrue();

        String retryAfterValue = capturedHeader(res2, "Retry-After");
        assertThat(retryAfterValue)
                .as("HTTP 429 must include Retry-After header")
                .isNotNull();

        // The Retry-After value must be a non-negative integer
        long retryAfterSecs = Long.parseLong(retryAfterValue);
        assertThat(retryAfterSecs)
                .as("Retry-After must be a non-negative number of seconds")
                .isGreaterThanOrEqualTo(0L);

        // ── Simulate TTL expiry: remove the exhausted bucket from the map ─────
        // This is equivalent to the real scenario where the client waits
        // retryAfterSecs seconds and the bucket refills (or the map entry expires).
        ConcurrentHashMap<String, Object> buckets = getBuckets(filter);
        String expectedBucketKey = "192.168.50.1:auth";
        assertThat(buckets.containsKey(expectedBucketKey))
                .as("Bucket key '%s' must exist in the map after exhaustion", expectedBucketKey)
                .isTrue();
        buckets.remove(expectedBucketKey);

        // ── Request #3: new bucket created lazily → must succeed ─────────────
        HttpServletResponse res3 = buildResponse();
        callDoFilter(filter, req, res3, chain);
        assertThat(wasBlocked(res3))
                .as("Third request after simulated TTL expiry must succeed (new bucket with full tokens)")
                .isFalse();

        // Filter chain must have been invoked for request #1 and request #3 (not #2)
        Mockito.verify(chain, Mockito.times(2)).doFilter(Mockito.any(), Mockito.any());

        // The bucket must have been re-created lazily by computeIfAbsent
        assertThat(buckets.containsKey(expectedBucketKey))
                .as("A fresh bucket must be present in the map after the third request")
                .isTrue();
    }
}
