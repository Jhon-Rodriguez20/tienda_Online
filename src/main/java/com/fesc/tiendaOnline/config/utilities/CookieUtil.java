package com.fesc.tiendaOnline.config.utilities;

import org.springframework.http.ResponseCookie;

/**
 * Centralized utility for building the refreshToken HttpOnly cookie
 * with environment-aware attributes.
 */
public class CookieUtil {

    private static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/api/auth/refresh";
    private static final long MAX_AGE_SECONDS = 604_800; // 7 days

    private CookieUtil() {
        // Utility class — prevent instantiation
    }

    /**
     * Builds a ResponseCookie carrying the refresh token value.
     *
     * @param tokenValue the refresh token string
     * @param isProd     true when the production profile is active
     * @return a ResponseCookie configured with HttpOnly, Path, MaxAge, Secure, and SameSite
     */
    public static ResponseCookie buildRefreshCookie(String tokenValue, boolean isProd) {
        return ResponseCookie.from(COOKIE_NAME, tokenValue)
                .httpOnly(true)
                .secure(isProd)
                .path(COOKIE_PATH)
                .maxAge(MAX_AGE_SECONDS)
                .sameSite(isProd ? "None" : "Lax")
                .build();
    }

    /**
     * Builds a deletion cookie (MaxAge=0) to instruct the browser to remove
     * the refreshToken cookie.
     *
     * @param isProd true when the production profile is active
     * @return a ResponseCookie that will expire the refreshToken cookie
     */
    public static ResponseCookie buildDeleteCookie(boolean isProd) {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(isProd)
                .path(COOKIE_PATH)
                .maxAge(0)
                .sameSite(isProd ? "None" : "Lax")
                .build();
    }
}
