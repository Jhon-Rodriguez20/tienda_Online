package com.fesc.tiendaOnline.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
// import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.exceptions.verification.WantedButNotInvoked;
import org.mockito.Mockito;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RateLimitingFilter}.
 *
 * Covers tasks 15.1 and 15.2:
 *
 * Task 15.1 — per-endpoint rate limits (Requirements 13.3, 13.4):
 *   - /auth/**      : max 10 req/min; request #11 → HTTP 429
 *   - /productos/** : max 100 req/min; request #101 → HTTP 429
 *   - /compras/**   : max 30 req/min; request #31 → HTTP 429
 *   - /usuarios/**  : max 20 req/min; request #21 → HTTP 429
 *
 * Task 15.2 — IP extraction and response headers (Requirements 13.2, 13.5, 13.6):
 *   - IP extraction from X-Forwarded-For (multiple IPs)
 *   - IP extraction via RemoteAddr when header absent
 *   - HTTP 429 includes Retry-After and JSON body
 *   - Successful responses include rate-limit headers
 *
 * No Spring context needed — all tests are plain JUnit 5.
 */
class RateLimitingFilterTest {

    // -----------------------------------------------------------------------
    // Reflection helpers
    // -----------------------------------------------------------------------

    private String invokeExtractClientIp(RateLimitingFilter filter,
                                          HttpServletRequest request) throws Exception {
        Method m = RateLimitingFilter.class.getDeclaredMethod("extractClientIp", HttpServletRequest.class);
        m.setAccessible(true);
        return (String) m.invoke(filter, request);
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

    private void setInt(RateLimitingFilter filter, String fieldName, int value) throws Exception {
        Field f = RateLimitingFilter.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.setInt(filter, value);
    }

    /** Find the index of a header name in a list (returns -1 if not found). */
    private int findHeaderIndex(List<String> names, String targetName) {
        for (int i = 0; i < names.size(); i++) {
            if (targetName.equals(names.get(i))) return i;
        }
        return -1;
    }

    // -----------------------------------------------------------------------
    // 15.1-1  testAuthEndpointLimit
    // Validates: Requirements 13.3, 13.4
    // -----------------------------------------------------------------------

    /**
     * /auth/** endpoint: 10 requests succeed; request #11 must return HTTP 429.
     */
    @Test
    void testAuthEndpointLimit() throws Exception {
        RateLimitingFilter filter = createFilter(10, 1, 100, 1, 30, 1, 20, 1);
        FilterChain chain = Mockito.mock(FilterChain.class);

        HttpServletRequest request = buildRequest("/auth/login", "1.1.1.1");

        sendRequestsExpectingSuccess(filter, chain, request, 10);
        assertRequestIsBlocked(filter, chain, request);

        Mockito.verify(chain, Mockito.times(10)).doFilter(Mockito.any(), Mockito.any());
    }

    // -----------------------------------------------------------------------
    // 15.1-2  testProductosEndpointLimit
    // Validates: Requirements 13.3, 13.4
    // -----------------------------------------------------------------------

    /**
     * /productos/** endpoint: 100 requests succeed; request #101 must return HTTP 429.
     */
    @Test
    void testProductosEndpointLimit() throws Exception {
        RateLimitingFilter filter = createFilter(10, 1, 100, 1, 30, 1, 20, 1);
        FilterChain chain = Mockito.mock(FilterChain.class);

        HttpServletRequest request = buildRequest("/productos/list", "2.2.2.2");

        sendRequestsExpectingSuccess(filter, chain, request, 100);
        assertRequestIsBlocked(filter, chain, request);

        Mockito.verify(chain, Mockito.times(100)).doFilter(Mockito.any(), Mockito.any());
    }

    // -----------------------------------------------------------------------
    // 15.1-3  testComprasEndpointLimit
    // Validates: Requirements 13.3, 13.4
    // -----------------------------------------------------------------------

    /**
     * /compras/** endpoint: 30 requests succeed; request #31 must return HTTP 429.
     */
    @Test
    void testComprasEndpointLimit() throws Exception {
        RateLimitingFilter filter = createFilter(10, 1, 100, 1, 30, 1, 20, 1);
        FilterChain chain = Mockito.mock(FilterChain.class);

        HttpServletRequest request = buildRequest("/compras/realizar", "3.3.3.3");

        sendRequestsExpectingSuccess(filter, chain, request, 30);
        assertRequestIsBlocked(filter, chain, request);

        Mockito.verify(chain, Mockito.times(30)).doFilter(Mockito.any(), Mockito.any());
    }

    // -----------------------------------------------------------------------
    // 15.1-4  testUsuariosEndpointLimit
    // Validates: Requirements 13.3, 13.4
    // -----------------------------------------------------------------------

    /**
     * /usuarios/** endpoint: 20 requests succeed; request #21 must return HTTP 429.
     */
    @Test
    void testUsuariosEndpointLimit() throws Exception {
        RateLimitingFilter filter = createFilter(10, 1, 100, 1, 30, 1, 20, 1);
        FilterChain chain = Mockito.mock(FilterChain.class);

        HttpServletRequest request = buildRequest("/usuarios/perfil", "4.4.4.4");

        sendRequestsExpectingSuccess(filter, chain, request, 20);
        assertRequestIsBlocked(filter, chain, request);

        Mockito.verify(chain, Mockito.times(20)).doFilter(Mockito.any(), Mockito.any());
    }

    // -----------------------------------------------------------------------
    // 15.2-1  testExtractIp_xForwardedForMultipleIps
    // Validates: Requirements 13.2
    // -----------------------------------------------------------------------

    /**
     * When X-Forwarded-For contains a comma-separated list of IPs,
     * only the first entry (trimmed) should be returned.
     */
    @Test
    void testExtractIp_xForwardedForMultipleIps() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1, 10.0.0.1");

        String result = invokeExtractClientIp(filter, request);

        assertThat(result)
                .as("Should extract the first IP from a comma-separated X-Forwarded-For list")
                .isEqualTo("192.168.1.1");
    }

    // -----------------------------------------------------------------------
    // 15.2-2  testExtractIp_noXForwardedFor
    // Validates: Requirements 13.2
    // -----------------------------------------------------------------------

    /**
     * When X-Forwarded-For is absent (null), the filter should fall back to
     * HttpServletRequest.getRemoteAddr().
     */
    @Test
    void testExtractIp_noXForwardedFor() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        Mockito.when(request.getRemoteAddr()).thenReturn("203.0.113.99");

        String result = invokeExtractClientIp(filter, request);

        assertThat(result)
                .as("Should return RemoteAddr when X-Forwarded-For header is absent")
                .isEqualTo("203.0.113.99");
    }

    // -----------------------------------------------------------------------
    // 15.2-3  testHttp429IncludesRetryAfterAndJsonBody
    // Validates: Requirements 13.5
    // -----------------------------------------------------------------------

    /**
     * After the configured auth limit is exhausted, the next request must receive:
     *   - HTTP status 429
     *   - Retry-After header
     *   - JSON body containing both "error" and "retryAfterSeconds" keys
     */
    @Test
    void testHttp429IncludesRetryAfterAndJsonBody() throws Exception {
        // Filter with auth limit = 1 so the second request is immediately blocked
        RateLimitingFilter filter = new RateLimitingFilter();
        setInt(filter, "authRequests",      1);
        setInt(filter, "authDuration",      1);
        setInt(filter, "productosRequests", 100);
        setInt(filter, "productosDuration", 1);
        setInt(filter, "comprasRequests",   30);
        setInt(filter, "comprasDuration",   1);
        setInt(filter, "usuariosRequests",  20);
        setInt(filter, "usuariosDuration",  1);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        FilterChain chain = Mockito.mock(FilterChain.class);

        Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        Mockito.when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        Mockito.when(request.getRequestURI()).thenReturn("/auth/login");

        // Request #1 — consumes the only token, should pass
        HttpServletResponse firstResponse = Mockito.mock(HttpServletResponse.class);
        StringWriter firstSw = new StringWriter();
        Mockito.when(firstResponse.getWriter()).thenReturn(new PrintWriter(firstSw));
        callDoFilter(filter, request, firstResponse, chain);

        // Request #2 — bucket is empty, should be blocked with HTTP 429
        HttpServletResponse blockedResponse = Mockito.mock(HttpServletResponse.class);
        StringWriter blockedSw = new StringWriter();
        PrintWriter blockedPw = new PrintWriter(blockedSw);
        Mockito.when(blockedResponse.getWriter()).thenReturn(blockedPw);

        ArgumentCaptor<String> headerNameCaptor  = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);

        callDoFilter(filter, request, blockedResponse, chain);
        blockedPw.flush();

        // Assert HTTP 429 status was set
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(blockedResponse).setStatus(statusCaptor.capture());
        assertThat(statusCaptor.getValue())
                .as("Second request (over limit of 1) must return HTTP 429")
                .isEqualTo(429);

        // Assert Retry-After header was added
        Mockito.verify(blockedResponse, Mockito.atLeastOnce())
               .addHeader(headerNameCaptor.capture(), headerValueCaptor.capture());

        List<String> names  = headerNameCaptor.getAllValues();
        // List<String> values = headerValueCaptor.getAllValues();

        int retryAfterIdx = findHeaderIndex(names, "Retry-After");
        assertThat(retryAfterIdx)
                .as("Retry-After header must be present on HTTP 429 response")
                .isGreaterThanOrEqualTo(0);

        // Assert JSON body contains required keys
        String body = blockedSw.toString();
        assertThat(body)
                .as("HTTP 429 body must contain the 'error' key")
                .contains("error");
        assertThat(body)
                .as("HTTP 429 body must contain the 'retryAfterSeconds' key")
                .contains("retryAfterSeconds");
    }

    // -----------------------------------------------------------------------
    // 15.2-4  testSuccessfulResponseIncludesRateLimitHeaders
    // Validates: Requirements 13.6
    // -----------------------------------------------------------------------

    /**
     * For a request that is within the configured limit, the response must include:
     *   - X-RateLimit-Remaining  with a non-negative numeric value
     *   - X-RateLimit-Limit      with the configured limit ("10")
     */
    @Test
    void testSuccessfulResponseIncludesRateLimitHeaders() throws Exception {
        // Filter with auth limit = 10 — a single request is well within the limit
        RateLimitingFilter filter = new RateLimitingFilter();
        setInt(filter, "authRequests",      10);
        setInt(filter, "authDuration",      1);
        setInt(filter, "productosRequests", 100);
        setInt(filter, "productosDuration", 1);
        setInt(filter, "comprasRequests",   30);
        setInt(filter, "comprasDuration",   1);
        setInt(filter, "usuariosRequests",  20);
        setInt(filter, "usuariosDuration",  1);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);

        Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        Mockito.when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        Mockito.when(request.getRequestURI()).thenReturn("/auth/login");
        StringWriter sw = new StringWriter();
        Mockito.when(response.getWriter()).thenReturn(new PrintWriter(sw));

        ArgumentCaptor<String> headerNameCaptor  = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);

        callDoFilter(filter, request, response, chain);

        // Verify headers were written
        Mockito.verify(response, Mockito.atLeastOnce())
               .addHeader(headerNameCaptor.capture(), headerValueCaptor.capture());

        List<String> names  = headerNameCaptor.getAllValues();
        List<String> values = headerValueCaptor.getAllValues();

        // X-RateLimit-Remaining must be present and non-negative
        int remainingIdx = findHeaderIndex(names, "X-RateLimit-Remaining");
        assertThat(remainingIdx)
                .as("X-RateLimit-Remaining header must be present on a successful response")
                .isGreaterThanOrEqualTo(0);

        long remaining = Long.parseLong(values.get(remainingIdx));
        assertThat(remaining)
                .as("X-RateLimit-Remaining must be a non-negative number")
                .isGreaterThanOrEqualTo(0L);

        // X-RateLimit-Limit must be present and equal to the configured limit "10"
        int limitIdx = findHeaderIndex(names, "X-RateLimit-Limit");
        assertThat(limitIdx)
                .as("X-RateLimit-Limit header must be present on a successful response")
                .isGreaterThanOrEqualTo(0);

        assertThat(values.get(limitIdx))
                .as("X-RateLimit-Limit must equal the configured auth limit (10)")
                .isEqualTo("10");

        // The filter chain must have been invoked (request was not blocked)
        Mockito.verify(chain).doFilter(Mockito.any(), Mockito.any());
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Create a RateLimitingFilter with all fields set via reflection.
     */
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

    /**
     * Build a mock request targeting the given URI and client IP (via RemoteAddr).
     */
    private HttpServletRequest buildRequest(String uri, String remoteAddr) {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getHeader("X-Forwarded-For")).thenReturn(null);
        Mockito.when(req.getRemoteAddr()).thenReturn(remoteAddr);
        Mockito.when(req.getRequestURI()).thenReturn(uri);
        return req;
    }

    /**
     * Send {@code count} requests and assert none of them returns HTTP 429.
     */
    private void sendRequestsExpectingSuccess(RateLimitingFilter filter,
                                              FilterChain chain,
                                              HttpServletRequest request,
                                              int count) throws Exception {
        for (int i = 0; i < count; i++) {
            HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
            StringWriter sw = new StringWriter();
            Mockito.when(resp.getWriter()).thenReturn(new PrintWriter(sw));

            callDoFilter(filter, request, resp, chain);

            // setStatus should NOT have been called with 429
            ArgumentCaptor<Integer> cap = ArgumentCaptor.forClass(Integer.class);
            try {
                Mockito.verify(resp).setStatus(cap.capture());
                assertThat(cap.getValue())
                        .as("Request %d/%d should NOT return HTTP 429", i + 1, count)
                        .isNotEqualTo(429);
            } catch (WantedButNotInvoked ignored) {
                // setStatus was not called at all → successful pass-through
            }
        }
    }

    /**
     * Send one additional request and assert it is rate-limited (HTTP 429).
     * Also verifies Retry-After header and JSON body content.
     */
    private void assertRequestIsBlocked(RateLimitingFilter filter,
                                         FilterChain chain,
                                         HttpServletRequest request) throws Exception {
        HttpServletResponse blockedResp = Mockito.mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Mockito.when(blockedResp.getWriter()).thenReturn(pw);

        ArgumentCaptor<String> headerName  = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> headerValue = ArgumentCaptor.forClass(String.class);

        callDoFilter(filter, request, blockedResp, chain);
        pw.flush();

        // HTTP 429 must be set
        ArgumentCaptor<Integer> statusCap = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(blockedResp).setStatus(statusCap.capture());
        assertThat(statusCap.getValue())
                .as("Request over limit must return HTTP 429")
                .isEqualTo(429);

        // Retry-After header must be present
        Mockito.verify(blockedResp, Mockito.atLeastOnce())
               .addHeader(headerName.capture(), headerValue.capture());
        int retryAfterIdx = findHeaderIndex(headerName.getAllValues(), "Retry-After");
        assertThat(retryAfterIdx)
                .as("Retry-After header must be present on HTTP 429")
                .isGreaterThanOrEqualTo(0);
        long retryAfterSecs = Long.parseLong(headerValue.getAllValues().get(retryAfterIdx));
        assertThat(retryAfterSecs)
                .as("Retry-After must be non-negative")
                .isGreaterThanOrEqualTo(0L);

        // JSON body must contain required keys
        String body = sw.toString();
        assertThat(body).as("HTTP 429 body must contain 'error'").contains("error");
        assertThat(body).as("HTTP 429 body must contain 'retryAfterSeconds'").contains("retryAfterSeconds");
    }
}

