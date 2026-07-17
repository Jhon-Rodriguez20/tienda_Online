# Cookie Persistence Fix — Bugfix Design

## Overview

The `refreshToken` HttpOnly cookie fails to persist across page reloads because the backend sets it with `Path=/auth/refresh` while the browser observes all refresh requests going to `/api/auth/refresh`. The browser's cookie path-matching algorithm requires the request URL path to **start with** the cookie's `Path` attribute. Since `/api/auth/refresh` does not start with `/auth/refresh`, the cookie is never sent on subsequent requests.

The fix is a single-constant change in `CookieUtil.java`: update `COOKIE_PATH` from `"/auth/refresh"` to `"/api/auth/refresh"` so the cookie path aligns with what the browser sees.

## Glossary

- **Bug_Condition (C)**: The condition that triggers the bug — when the cookie `Path` attribute does not match the browser-visible request path for refresh requests, causing the browser to omit the cookie.
- **Property (P)**: The desired behavior — the `refreshToken` cookie SHALL be attached by the browser to refresh requests because the cookie path matches the browser-visible request URL.
- **Preservation**: Existing cookie security attributes (`HttpOnly`, `Secure`, `SameSite`, `MaxAge`), login/logout flows, and frontend request patterns that must remain unchanged.
- **CookieUtil**: Utility class in `config/utilities/CookieUtil.java` responsible for building the `refreshToken` ResponseCookie with environment-aware attributes.
- **COOKIE_PATH**: The `Path` attribute value set on the `refreshToken` cookie, currently `"/auth/refresh"` — the root cause of the bug.
- **Browser Path-Matching**: Per RFC 6265 §5.1.4, a cookie is included in a request only if the request URI path starts with the cookie's `Path` value.

## Bug Details

### Bug Condition

The bug manifests when the backend sets the `refreshToken` cookie with a path that does not match the browser-visible URL for refresh requests. The frontend base URL is `/api`, so all requests go to `/api/auth/refresh`, but the cookie is scoped to `/auth/refresh`. The browser never attaches it.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type { cookiePath: string, browserRequestPath: string }
  OUTPUT: boolean

  RETURN input.browserRequestPath does NOT start with input.cookiePath
         AND input.browserRequestPath targets the refresh endpoint
END FUNCTION
```

In concrete terms for this codebase:
```
isBugCondition = (cookiePath == "/auth/refresh") AND (browserRequestPath == "/api/auth/refresh")
```

### Examples

- **Login → Refresh on reload**: User logs in successfully (cookie set with `Path=/auth/refresh`). User reloads page. Frontend calls `POST /api/auth/refresh` with `withCredentials: true`. Browser does NOT attach the cookie → backend returns 401 → session lost.
- **Login → Immediate refresh (interceptor retry)**: User's access token expires mid-session. Interceptor calls `POST /api/auth/refresh`. Browser does NOT attach cookie → 401 → user is logged out.
- **Login → Stay on same tab**: First login works fine because the access token is in memory. The bug only surfaces when the in-memory token is lost (page reload, new tab).
- **Edge case — Delete cookie on logout**: Logout sets `MaxAge=0` with `Path=/auth/refresh`. Since the browser never stored a cookie at that path (from its perspective), the delete is a no-op — but this is harmless since the cookie was never persisted anyway.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- The cookie MUST remain `HttpOnly=true` (not accessible via JavaScript)
- The cookie MUST retain `MaxAge=604800` (7 days) for set operations and `MaxAge=0` for delete operations
- The cookie `Secure` attribute MUST continue to be `true` in production and `false` in development
- The cookie `SameSite` attribute MUST continue to be `"None"` in production and `"Lax"` in development
- The frontend MUST continue to send refresh requests with `withCredentials: true` and no request body
- The backend MUST continue to return 401 when no valid `refreshToken` cookie is present
- Login and logout flows MUST continue to work identically (setting/deleting the cookie)
- CORS configuration MUST remain unchanged (`Access-Control-Allow-Credentials: true`, explicit origins)

**Scope:**
All inputs that do NOT involve the cookie `Path` attribute should be completely unaffected by this fix. This includes:
- Cookie name (`"refreshToken"`)
- Cookie value (the actual refresh token string)
- Cookie security attributes (`HttpOnly`, `Secure`, `SameSite`, `MaxAge`)
- Frontend request structure (URL, method, headers, body)
- Backend refresh logic (token validation, rotation, response format)
- Proxy and rewrite configurations (Angular proxy, Vercel rewrites)

## Hypothesized Root Cause

Based on the code analysis, the root cause is definitively identified:

1. **Incorrect COOKIE_PATH constant**: `CookieUtil.java` defines `COOKIE_PATH = "/auth/refresh"`. This path represents the **backend's** internal route, not the **browser-visible** URL path. The frontend prefixes all requests with `/api`, so the browser sees the request going to `/api/auth/refresh`.

2. **Path-matching semantics misunderstanding**: The `Path` cookie attribute is matched against what the **browser** sees as the request URL, not what the backend receives after proxy/rewrite stripping. The Angular dev proxy (`pathRewrite: "^/api" → ""`) and Vercel rewrite (`/api/:path* → backend/:path*`) strip `/api` for the backend, but the browser still observes `/api/auth/refresh` as the request URL.

3. **Single point of failure**: Both `buildRefreshCookie()` and `buildDeleteCookie()` use the same `COOKIE_PATH` constant, so the fix automatically applies to cookie deletion as well, maintaining consistency.

## Correctness Properties

Property 1: Bug Condition - Cookie Path Matches Browser Request Path

_For any_ refresh request where the frontend sends `POST /api/auth/refresh` with `withCredentials: true`, the cookie set by the backend SHALL have a `Path` attribute that causes the browser to attach it on subsequent requests to the same URL — specifically, the cookie `Path` must be a prefix of the browser-visible request path `/api/auth/refresh`.

**Validates: Requirements 2.1, 2.2, 2.3**

Property 2: Preservation - Cookie Security Attributes Unchanged

_For any_ cookie-setting operation (login or refresh), the fixed `CookieUtil` SHALL produce a cookie with identical `HttpOnly`, `Secure`, `SameSite`, `MaxAge`, and `name` attributes as the original implementation, preserving all security properties. Only the `Path` attribute changes.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `src/main/java/com/fesc/tiendaOnline/config/utilities/CookieUtil.java`

**Constant**: `COOKIE_PATH`

**Specific Changes**:
1. **Update COOKIE_PATH value**: Change from `"/auth/refresh"` to `"/api/auth/refresh"` so the cookie path matches the browser-visible request URL.
   - Line: `private static final String COOKIE_PATH = "/auth/refresh";`
   - Becomes: `private static final String COOKIE_PATH = "/api/auth/refresh";`

2. **No other file changes required**: Both `buildRefreshCookie()` and `buildDeleteCookie()` reference `COOKIE_PATH`, so both will automatically use the corrected path.

3. **No frontend changes**: The frontend already sends requests to `/api/auth/refresh` with `withCredentials: true` — this is correct behavior.

4. **No proxy/rewrite changes**: The proxy (dev) and Vercel rewrite (prod) configurations are correct — they strip `/api` before forwarding to the backend. The cookie path must match what the BROWSER sees, not what the backend receives.

5. **No SecurityConfig changes**: The backend's `permitAll()` rule for `/auth/refresh` is based on the path AFTER proxy stripping, which remains unchanged.

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write a unit test for `CookieUtil` that asserts the cookie `Path` attribute starts with `/api/auth/refresh`. Run this test on the UNFIXED code to observe it failing (confirming the path is currently wrong).

**Test Cases**:
1. **buildRefreshCookie path mismatch**: Call `CookieUtil.buildRefreshCookie("token", false)` and assert `cookie.getPath()` equals `"/api/auth/refresh"` (will fail on unfixed code — returns `"/auth/refresh"`)
2. **buildDeleteCookie path mismatch**: Call `CookieUtil.buildDeleteCookie(false)` and assert `cookie.getPath()` equals `"/api/auth/refresh"` (will fail on unfixed code)
3. **Browser path-match simulation**: Assert that `/api/auth/refresh` starts with `cookie.getPath()` (will fail on unfixed code since `/api/auth/refresh` does not start with `/auth/refresh`)

**Expected Counterexamples**:
- `cookie.getPath()` returns `"/auth/refresh"` instead of `"/api/auth/refresh"`
- Path-matching assertion fails because `"/api/auth/refresh".startsWith("/auth/refresh")` is `false`

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  cookie := CookieUtil_fixed.buildRefreshCookie(input.tokenValue, input.isProd)
  ASSERT "/api/auth/refresh".startsWith(cookie.getPath()) == true
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function — specifically, all cookie attributes EXCEPT `Path` remain identical.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  cookieFixed := CookieUtil_fixed.buildRefreshCookie(input.tokenValue, input.isProd)
  ASSERT cookieFixed.getName() == "refreshToken"
  ASSERT cookieFixed.isHttpOnly() == true
  ASSERT cookieFixed.getMaxAge() == Duration.ofSeconds(604800)
  ASSERT cookieFixed.getSecure() == input.isProd
  ASSERT cookieFixed.getSameSite() == (input.isProd ? "None" : "Lax")
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many combinations of `tokenValue` (arbitrary strings) and `isProd` (boolean) automatically
- It catches edge cases like empty tokens, very long tokens, or tokens with special characters
- It provides strong guarantees that security attributes are always correctly set regardless of input

**Test Plan**: Observe cookie attributes on UNFIXED code for various inputs, then write property-based tests verifying these attributes remain unchanged after the fix.

**Test Cases**:
1. **HttpOnly preservation**: For any token value and isProd flag, the cookie always has `HttpOnly=true`
2. **MaxAge preservation**: For any token value and isProd flag, the refresh cookie always has `MaxAge=604800`
3. **Secure attribute preservation**: `Secure` matches `isProd` for all inputs
4. **SameSite attribute preservation**: `SameSite` is `"None"` when `isProd=true`, `"Lax"` when `isProd=false`
5. **Delete cookie preservation**: `buildDeleteCookie` always produces `MaxAge=0` with correct path

### Unit Tests

- Test that `buildRefreshCookie` returns cookie with `Path=/api/auth/refresh`
- Test that `buildDeleteCookie` returns cookie with `Path=/api/auth/refresh` and `MaxAge=0`
- Test that cookie name is always `"refreshToken"`
- Test `HttpOnly=true` for both prod and dev
- Test `Secure=true` in prod, `Secure=false` in dev
- Test `SameSite=None` in prod, `SameSite=Lax` in dev

### Property-Based Tests

- Generate random token strings (including empty, whitespace, special chars, long strings) and verify cookie `Path` is always `/api/auth/refresh`
- Generate random boolean `isProd` values and verify `Secure` and `SameSite` attributes are always consistent with the flag
- Generate random token strings and verify `HttpOnly` is always `true` and `MaxAge` is always `604800` for refresh cookies and `0` for delete cookies

### Integration Tests

- Test full login → page reload → silent refresh flow to verify cookie is attached
- Test login → access token expiry → interceptor refresh retry to verify cookie is attached
- Test logout → verify delete cookie has correct path so browser removes it
- Test that CORS headers continue to include `Access-Control-Allow-Credentials: true`
