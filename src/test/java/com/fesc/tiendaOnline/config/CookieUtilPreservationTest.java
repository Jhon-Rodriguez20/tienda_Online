package com.fesc.tiendaOnline.config;

import com.fesc.tiendaOnline.config.utilities.CookieUtil;
import net.jqwik.api.*;
import org.springframework.http.ResponseCookie;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based preservation tests for {@link CookieUtil}.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
 *
 * Property 2: Preservation — Cookie Security Attributes Unchanged.
 * These tests confirm that all cookie attributes EXCEPT Path remain correct
 * on the UNFIXED code. After the fix is applied, these tests must continue
 * to pass, proving no regressions to security attributes.
 *
 * NOTE: Path is intentionally NOT asserted here — it is the attribute
 * being changed by the fix.
 */
class CookieUtilPreservationTest {

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
    // Property: buildRefreshCookie always preserves core attributes
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
     *
     * For any random token string and boolean isProd,
     * buildRefreshCookie(token, isProd) always has:
     * - HttpOnly = true
     * - name = "refreshToken"
     * - MaxAge = 604800
     * - value = token
     */
    @Property(tries = 100)
    void buildRefreshCookie_alwaysPreservesCoreAttributes(
            @ForAll("tokenStrings") String token,
            @ForAll boolean isProd) {

        ResponseCookie cookie = CookieUtil.buildRefreshCookie(token, isProd);

        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEqualTo(token);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(604_800L);
    }

    // -----------------------------------------------------------------------
    // Property: buildRefreshCookie environment-aware Secure and SameSite
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
     *
     * For any random token string and boolean isProd:
     * - Secure == isProd
     * - SameSite == (isProd ? "None" : "Lax")
     */
    @Property(tries = 100)
    void buildRefreshCookie_secureAndSameSiteMatchEnvironment(
            @ForAll("tokenStrings") String token,
            @ForAll boolean isProd) {

        ResponseCookie cookie = CookieUtil.buildRefreshCookie(token, isProd);

        assertThat(cookie.isSecure()).isEqualTo(isProd);
        assertThat(cookie.getSameSite()).isEqualTo(isProd ? "None" : "Lax");
    }

    // -----------------------------------------------------------------------
    // Property: buildDeleteCookie always preserves security attributes
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
     *
     * For any boolean isProd, buildDeleteCookie(isProd) always has:
     * - HttpOnly = true
     * - MaxAge = 0
     * - name = "refreshToken"
     * - value = ""
     * - Secure == isProd
     * - SameSite == (isProd ? "None" : "Lax")
     */
    @Property(tries = 100)
    void buildDeleteCookie_alwaysPreservesSecurityAttributes(
            @ForAll boolean isProd) {

        ResponseCookie cookie = CookieUtil.buildDeleteCookie(isProd);

        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(0L);
        assertThat(cookie.isSecure()).isEqualTo(isProd);
        assertThat(cookie.getSameSite()).isEqualTo(isProd ? "None" : "Lax");
    }
}
