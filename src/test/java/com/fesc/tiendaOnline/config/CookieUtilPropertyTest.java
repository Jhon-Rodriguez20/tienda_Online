package com.fesc.tiendaOnline.config;

import com.fesc.tiendaOnline.config.utilities.CookieUtil;
import net.jqwik.api.*;
import org.springframework.http.ResponseCookie;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for {@link CookieUtil} using jqwik.
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7
 *
 * Property 1: Login sets correct cookie attributes —
 * buildRefreshCookie() always produces HttpOnly=true, Path=/api/auth/refresh,
 * MaxAge=604800, and environment-aware SameSite/Secure values.
 * buildDeleteCookie() always produces MaxAge=0 with same security attributes.
 */
class CookieUtilPropertyTest {

    // -----------------------------------------------------------------------
    // Custom Arbitrary: non-empty token strings
    // -----------------------------------------------------------------------

    @Provide
    Arbitrary<String> tokenStrings() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(512)
                .alpha().numeric();
    }

    // -----------------------------------------------------------------------
    // Property: buildRefreshCookie in production mode
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7
     *
     * For any random token string in production mode (isProd=true),
     * buildRefreshCookie must produce a cookie with:
     * - name = "refreshToken"
     * - HttpOnly = true
     * - Path = "/api/auth/refresh"
     * - MaxAge = 604800
     * - Secure = true
     * - SameSite = "None"
     */
    @Property(tries = 100)
    void buildRefreshCookie_prod_alwaysSetsCorrectAttributes(
            @ForAll("tokenStrings") String token) {

        ResponseCookie cookie = CookieUtil.buildRefreshCookie(token, true);

        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEqualTo(token);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth/refresh");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(604_800L);
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("None");
    }

    // -----------------------------------------------------------------------
    // Property: buildRefreshCookie in non-production mode
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7
     *
     * For any random token string in non-production mode (isProd=false),
     * buildRefreshCookie must produce a cookie with:
     * - name = "refreshToken"
     * - HttpOnly = true
     * - Path = "/api/auth/refresh"
     * - MaxAge = 604800
     * - Secure = false
     * - SameSite = "Lax"
     */
    @Property(tries = 100)
    void buildRefreshCookie_nonProd_alwaysSetsCorrectAttributes(
            @ForAll("tokenStrings") String token) {

        ResponseCookie cookie = CookieUtil.buildRefreshCookie(token, false);

        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEqualTo(token);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth/refresh");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(604_800L);
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    // -----------------------------------------------------------------------
    // Property: buildDeleteCookie in production mode
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7
     *
     * buildDeleteCookie in production mode (isProd=true) must produce:
     * - name = "refreshToken"
     * - value = ""
     * - HttpOnly = true
     * - Path = "/api/auth/refresh"
     * - MaxAge = 0
     * - Secure = true
     * - SameSite = "None"
     */
    @Property(tries = 100)
    void buildDeleteCookie_prod_alwaysSetsCorrectAttributes(
            @ForAll("tokenStrings") String ignoredToken) {

        ResponseCookie cookie = CookieUtil.buildDeleteCookie(true);

        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth/refresh");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(0L);
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("None");
    }

    // -----------------------------------------------------------------------
    // Property: buildDeleteCookie in non-production mode
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7
     *
     * buildDeleteCookie in non-production mode (isProd=false) must produce:
     * - name = "refreshToken"
     * - value = ""
     * - HttpOnly = true
     * - Path = "/api/auth/refresh"
     * - MaxAge = 0
     * - Secure = false
     * - SameSite = "Lax"
     */
    @Property(tries = 100)
    void buildDeleteCookie_nonProd_alwaysSetsCorrectAttributes(
            @ForAll("tokenStrings") String ignoredToken) {

        ResponseCookie cookie = CookieUtil.buildDeleteCookie(false);

        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth/refresh");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(0L);
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }
}
