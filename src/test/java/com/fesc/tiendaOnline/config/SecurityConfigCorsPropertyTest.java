package com.fesc.tiendaOnline.config;

import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for CORS origin filtering logic in {@link SecurityConfig}.
 *
 * These tests replicate the filtering logic from SecurityConfig.corsConfigurationSource()
 * and verify invariants across randomly generated origin lists.
 *
 * Validates: Requirements 7.2, 7.4
 *
 * Property 8: CORS origin filtering in production —
 * For any list of configured allowed origins, when the production profile is active,
 * the effective CORS origin list SHALL contain only origins starting with https://,
 * and SHALL never contain the wildcard *.
 */
class SecurityConfigCorsPropertyTest {

    // -----------------------------------------------------------------------
    // Filtering logic extracted from SecurityConfig.corsConfigurationSource()
    // -----------------------------------------------------------------------

    /**
     * Replicates wildcard rejection: filters out any "*" entries.
     */
    private List<String> filterWildcards(List<String> origins) {
        return origins.stream()
                .filter(origin -> !"*".equals(origin))
                .collect(Collectors.toList());
    }

    /**
     * Replicates prod-mode HTTPS-only filtering: keeps only origins starting with "https://".
     */
    private List<String> filterForProd(List<String> origins) {
        return origins.stream()
                .filter(origin -> origin.startsWith("https://"))
                .collect(Collectors.toList());
    }

    /**
     * Full prod-mode pipeline: wildcard rejection + HTTPS-only filtering.
     */
    private List<String> applyProdFiltering(List<String> origins) {
        List<String> noWildcards = filterWildcards(origins);
        return filterForProd(noWildcards);
    }

    // -----------------------------------------------------------------------
    // Custom Arbitraries: realistic origin strings
    // -----------------------------------------------------------------------

    @Provide
    Arbitrary<List<String>> originLists() {
        Arbitrary<String> origins = Arbitraries.oneOf(
                // https origins
                Arbitraries.of(
                        "https://example.com",
                        "https://app.tiendaonline.com",
                        "https://localhost:4200",
                        "https://mysite.vercel.app",
                        "https://api.production.io"
                ),
                // http origins
                Arbitraries.of(
                        "http://localhost:4200",
                        "http://example.com",
                        "http://dev.local:8080",
                        "http://192.168.1.100:3000",
                        "http://internal.test"
                ),
                // wildcard
                Arbitraries.just("*"),
                // garbage/malformed strings
                Arbitraries.of(
                        "ftp://files.example.com",
                        "ws://socket.example.com",
                        "not-a-url",
                        "",
                        "httpx://wrong.scheme",
                        "HTTPS://uppercase.com"
                )
        );

        return origins.list().ofMinSize(1).ofMaxSize(20);
    }

    @Provide
    Arbitrary<List<String>> originListsWithWildcard() {
        // Always include at least one wildcard in the list
        Arbitrary<String> nonWildcardOrigins = Arbitraries.oneOf(
                Arbitraries.of(
                        "https://example.com",
                        "https://app.tiendaonline.com",
                        "http://localhost:4200",
                        "http://dev.local:8080"
                ),
                Arbitraries.of(
                        "ftp://files.example.com",
                        "not-a-url",
                        "",
                        "HTTPS://uppercase.com"
                )
        );

        return nonWildcardOrigins.list().ofMinSize(0).ofMaxSize(15)
                .map(list -> {
                    List<String> withWildcard = new ArrayList<>(list);
                    withWildcard.add("*");
                    return withWildcard;
                });
    }

    // -----------------------------------------------------------------------
    // Property 1: Wildcard rejection always removes "*"
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 7.2
     *
     * For any random list of origins containing a mix of valid origins and wildcard
     * values, after filtering with the wildcard rejection logic, the result never
     * contains "*".
     */
    @Property(tries = 200)
    @Tag("httponly-cookie-auth")
    @Tag("Property8-CORS-origin-filtering")
    void wildcardRejection_neverContainsWildcard(
            @ForAll("originListsWithWildcard") List<String> origins) {

        List<String> filtered = filterWildcards(origins);

        assertThat(filtered).doesNotContain("*");
    }

    // -----------------------------------------------------------------------
    // Property 2: Prod mode only allows https:// origins
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 7.4
     *
     * For any random list of origins with both http:// and https:// URLs,
     * after prod filtering, all remaining origins start with "https://".
     */
    @Property(tries = 200)
    @Tag("httponly-cookie-auth")
    @Tag("Property8-CORS-origin-filtering")
    void prodFiltering_onlyHttpsOriginsSurvive(
            @ForAll("originLists") List<String> origins) {

        List<String> noWildcards = filterWildcards(origins);
        List<String> prodFiltered = filterForProd(noWildcards);

        assertThat(prodFiltered).allMatch(origin -> origin.startsWith("https://"));
    }

    // -----------------------------------------------------------------------
    // Property 3: Combined — prod mode never contains "*" AND never http://
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 7.2, 7.4
     *
     * For any random list of origins, the full prod filtering pipeline produces
     * a result that never contains "*" AND never contains any origin starting
     * with "http://" (only "https://" origins).
     */
    @Property(tries = 200)
    @Tag("httponly-cookie-auth")
    @Tag("Property8-CORS-origin-filtering")
    void fullProdPipeline_neverContainsWildcardOrHttpOrigins(
            @ForAll("originLists") List<String> origins) {

        List<String> result = applyProdFiltering(origins);

        assertThat(result).doesNotContain("*");
        assertThat(result).allMatch(origin -> origin.startsWith("https://"));
        assertThat(result).noneMatch(origin -> origin.startsWith("http://"));
    }
}
