package com.fesc.tiendaOnline.config;

import com.fesc.tiendaOnline.config.utilities.CookieUtil;
import net.jqwik.api.*;
import org.springframework.http.ResponseCookie;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug Condition Exploration Test for the refreshToken cookie path mismatch.
 *
 * Validates: Requirements 1.1, 1.2, 2.1
 *
 * Property 1: Bug Condition — Cookie Path Does Not Match Browser-Visible Refresh URL
 *
 * The browser sends refresh requests to /api/auth/refresh. For the cookie to be
 * attached, the cookie's Path attribute must be a prefix of the request URL path.
 * This test asserts the EXPECTED correct behavior: cookie.getPath() == "/api/auth/refresh".
 *
 * On UNFIXED code this test WILL FAIL because the cookie path is "/auth/refresh",
 * which is NOT a prefix of "/api/auth/refresh". This failure confirms the bug exists.
 */
class CookieUtilBugConditionTest {

    private static final String EXPECTED_COOKIE_PATH = "/api/auth/refresh";
    private static final String BROWSER_REFRESH_URL_PATH = "/api/auth/refresh";

    // -----------------------------------------------------------------------
    // Custom Arbitrary: random token strings
    // -----------------------------------------------------------------------

    @Provide
    Arbitrary<String> tokenStrings() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(512)
                .alpha().numeric();
    }

    // -----------------------------------------------------------------------
    // Property: buildRefreshCookie path matches browser-visible refresh URL
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 1.1, 1.2, 2.1
     *
     * For any random token string and any isProd value, the cookie path returned
     * by buildRefreshCookie MUST equal "/api/auth/refresh" so that the browser
     * attaches the cookie when requesting POST /api/auth/refresh.
     *
     * EXPECTED TO FAIL on unfixed code — confirms the bug.
     */
    @Property(tries = 100)
    void buildRefreshCookie_path_matchesBrowserVisibleRefreshUrl(
            @ForAll("tokenStrings") String token,
            @ForAll boolean isProd) {

        ResponseCookie cookie = CookieUtil.buildRefreshCookie(token, isProd);

        // Assert cookie path equals the expected correct path
        assertThat(cookie.getPath())
                .as("Cookie path must equal the browser-visible refresh endpoint path")
                .isEqualTo(EXPECTED_COOKIE_PATH);

        // Simulate browser path-matching: request URL path must start with cookie path
        assertThat(BROWSER_REFRESH_URL_PATH.startsWith(cookie.getPath()))
                .as("Browser path-matching: '/api/auth/refresh' must start with cookie path '%s'",
                        cookie.getPath())
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // Property: buildDeleteCookie path matches browser-visible refresh URL
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 1.1, 1.2, 2.1
     *
     * For any isProd value, the cookie path returned by buildDeleteCookie MUST
     * equal "/api/auth/refresh" so the browser can properly remove the cookie
     * when the user logs out.
     *
     * EXPECTED TO FAIL on unfixed code — confirms the bug.
     */
    @Property(tries = 100)
    void buildDeleteCookie_path_matchesBrowserVisibleRefreshUrl(
            @ForAll boolean isProd) {

        ResponseCookie cookie = CookieUtil.buildDeleteCookie(isProd);

        // Assert cookie path equals the expected correct path
        assertThat(cookie.getPath())
                .as("Delete cookie path must equal the browser-visible refresh endpoint path")
                .isEqualTo(EXPECTED_COOKIE_PATH);

        // Simulate browser path-matching: request URL path must start with cookie path
        assertThat(BROWSER_REFRESH_URL_PATH.startsWith(cookie.getPath()))
                .as("Browser path-matching: '/api/auth/refresh' must start with cookie path '%s'",
                        cookie.getPath())
                .isTrue();
    }
}
