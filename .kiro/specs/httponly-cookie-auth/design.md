# Design Document: HttpOnly Cookie Auth

## Overview

This design migrates the Refresh Token transport from `localStorage` (readable by JavaScript) to an **HttpOnly cookie** set by the Spring Boot backend. The Access Token remains exclusively in-memory (Angular Signal) on the frontend. This architectural change eliminates XSS-based token theft for refresh tokens while preserving the existing JWT generation, RSA signing, rotation, and revocation logic.

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Refresh Token in HttpOnly cookie | Prevents XSS access — JavaScript cannot read HttpOnly cookies |
| Access Token in-memory only | Short-lived (15 min) and never persisted — tab close = token gone |
| Profile data still in localStorage | Enables UI hydration on page reload; non-sensitive user metadata |
| Silent refresh on app init | Restores session after page reload using the surviving HttpOnly cookie |
| SameSite varies by environment | `None` required for cross-origin prod (Vercel→Render); `Lax` sufficient for same-origin dev/Docker |
| Cookie `Path=/auth/refresh` | Browser only sends cookie to the refresh endpoint, minimizing cookie surface |

## Architecture

```mermaid
sequenceDiagram
    participant Browser
    participant Angular as Angular SPA
    participant Backend as Spring Boot API

    Note over Browser,Backend: LOGIN FLOW
    Angular->>Backend: POST /auth/login {email, contrasena}
    Backend-->>Angular: 200 OK + Authorization: Bearer <AT> + Set-Cookie: refreshToken=<RT>; HttpOnly; Path=/auth/refresh
    Angular->>Angular: Store AT in Auth_State signal (memory only)
    Angular->>Browser: Store profile fields in localStorage

    Note over Browser,Backend: REFRESH FLOW (silent or on 401)
    Angular->>Backend: POST /auth/refresh (no body, withCredentials:true)
    Note right of Browser: Browser auto-attaches refreshToken cookie
    Backend-->>Angular: 200 OK + Authorization: Bearer <newAT> + Set-Cookie: refreshToken=<newRT>
    Angular->>Angular: Update AT in Auth_State signal

    Note over Browser,Backend: LOGOUT FLOW
    Angular->>Backend: POST /auth/logout (Authorization: Bearer <AT>, withCredentials:true)
    Backend-->>Angular: 204 No Content + Set-Cookie: refreshToken=; MaxAge=0
    Angular->>Angular: Clear Auth_State signal + localStorage profile
```

## Components and Interfaces

### Backend Components

#### 1. `CookieUtil` (New Utility Class)

A centralized utility responsible for building the `refreshToken` cookie with environment-aware attributes.

```java
package com.fesc.tiendaOnline.config;

import org.springframework.http.ResponseCookie;

public class CookieUtil {

    private static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/auth/refresh";
    private static final long MAX_AGE_SECONDS = 604_800; // 7 days

    public static ResponseCookie buildRefreshCookie(String tokenValue, boolean isProd) {
        return ResponseCookie.from(COOKIE_NAME, tokenValue)
                .httpOnly(true)
                .secure(isProd)
                .path(COOKIE_PATH)
                .maxAge(MAX_AGE_SECONDS)
                .sameSite(isProd ? "None" : "Lax")
                .build();
    }

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
```

#### 2. `AuthController` (Modified)

Changes:
- **Login**: Adds `Set-Cookie` header via `ResponseCookie`, removes `refreshToken` from response body.
- **Refresh**: Reads token from `@CookieValue` annotation instead of `@RequestBody`. No request body required.
- **Logout**: Adds deletion cookie (`MaxAge=0`) to response.

```java
@PostMapping("/login")
public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
    // Delegates to AuthService which returns body + refreshToken value
    AuthResult result = authService.login(loginRequest);
    ResponseCookie cookie = CookieUtil.buildRefreshCookie(result.refreshToken(), isProd);
    return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + result.accessToken())
            .body(result.responseBody()); // body has refreshToken=null
}

@PostMapping("/refresh")
public ResponseEntity<LoginResponseDTO> refresh(
        @CookieValue(name = "refreshToken", required = false) String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
        throw new UnauthorizedException("Refresh token ausente");
    }
    AuthResult result = authService.refreshAccessToken(refreshToken);
    ResponseCookie cookie = CookieUtil.buildRefreshCookie(result.refreshToken(), isProd);
    return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + result.accessToken())
            .body(result.responseBody());
}

@PostMapping("/logout")
public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
    String token = authHeader.substring(7);
    String jti = jwtService.extractJti(token);
    UUID idUsuario = jwtService.extractIdUsuario(token);
    authService.logout(jti, idUsuario);
    ResponseCookie deleteCookie = CookieUtil.buildDeleteCookie(isProd);
    return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
            .build();
}
```

#### 3. `AuthService` (Modified Return Type)

The service method signatures change to return an internal `AuthResult` record instead of `ResponseEntity`, separating HTTP concerns (cookie, headers) from business logic:

```java
public record AuthResult(
    String accessToken,
    String refreshToken,
    LoginResponseDTO responseBody
) {}
```

The `LoginResponseDTO` body will have its `refreshToken` field set to `null` (or the field removed via `@JsonInclude(NON_NULL)`).

#### 4. `SecurityConfig` (Modified CORS)

Adds `"Set-Cookie"` to exposed headers:

```java
configuration.setExposedHeaders(Arrays.asList(
    "Authorization", "Idempotency-Replayed",
    "X-RateLimit-Remaining", "X-RateLimit-Limit", "Retry-After",
    "Set-Cookie"
));
```

#### 5. `LoginResponseDTO` (Modified)

The `refreshToken` field is annotated with `@JsonInclude(JsonInclude.Include.NON_NULL)` or removed. The controller sets it to `null` before serialization.

### Frontend Components

#### 1. `AuthState` Interface (Modified)

Remove `refreshToken` and `expiraEn` fields:

```typescript
export interface AuthState {
  isAuthenticated: boolean;
  idUsuario:       string | null;
  nombre:          string | null;
  email:           string | null;
  rol:             'CLIENTE' | 'ADMIN' | null;
  telefono:        string | null;
  pais:            string | null;
  ciudad:          string | null;
  direccion:       string | null;
  departamento:    string | null;
  codigoPostal:    string | null;
  urlImagen:       string | null;
  accessToken:     string | null;  // in-memory only
}
```

#### 2. `LoginResponseDTO` Interface (Modified)

Remove `refreshToken` field:

```typescript
export interface LoginResponseDTO {
  idUsuario:    string;
  nombre:       string;
  email:        string;
  rol:          'CLIENTE' | 'ADMIN';
  telefono:     string;
  pais:         string;
  ciudad:       string;
  direccion:    string;
  departamento: string;
  codigoPostal: string;
  urlImagen:    string | null;
  expiraEn:     number;
}
```

#### 3. `RefreshRequestDTO` (Removed)

No longer needed — refresh sends no body.

#### 4. `AuthService` (Modified)

- **Login**: No longer stores `accessToken`, `refreshToken`, or `expiraEn` in localStorage. Only stores profile fields. Access token goes to `Auth_State` signal.
- **Refresh**: Sends `POST /auth/refresh` with empty body and `{ withCredentials: true }`. No `RefreshRequestDTO`.
- **Logout**: Sends with `{ withCredentials: true }` so browser attaches cookie for deletion confirmation.
- **Hydration** (`_hydrateFromStorage`): If profile data exists in localStorage but no access token in memory, issues silent refresh. On failure, clears localStorage and redirects to `/login`.

```typescript
refresh(): Observable<LoginResponseDTO> {
  return this.http
    .post<LoginResponseDTO>(
      `${this.base}/auth/refresh`,
      null,  // no body
      { observe: 'response', withCredentials: true },
    )
    .pipe(
      tap((res: HttpResponse<LoginResponseDTO>) => {
        const body = res.body!;
        const authHeader = res.headers.get('Authorization') ?? '';
        const accessToken = authHeader.startsWith('Bearer ')
          ? authHeader.slice(7) : '';
        this._storeSession(body, accessToken);
      }),
      map((res) => res.body!),
    );
}
```

#### 5. `Auth Interceptor` (Modified)

- Adds `withCredentials: true` when URL contains `/auth/refresh` or `/auth/logout`.
- Does NOT add `withCredentials` to other requests (Access Token is sent via Authorization header).
- Reads Access Token from `Auth_State` signal, never from localStorage.
- On 401 retry, calls refresh with `withCredentials: true`.

## Data Models

### Cookie Structure

| Attribute | Login/Refresh (Dev/Docker) | Login/Refresh (Prod) | Logout (all) |
|-----------|---------------------------|---------------------|--------------|
| Name | `refreshToken` | `refreshToken` | `refreshToken` |
| Value | UUID token string | UUID token string | `""` (empty) |
| HttpOnly | `true` | `true` | `true` |
| Secure | `false` | `true` | matches env |
| SameSite | `Lax` | `None` | matches env |
| Path | `/auth/refresh` | `/auth/refresh` | `/auth/refresh` |
| MaxAge | `604800` (7 days) | `604800` (7 days) | `0` |

### AuthResult Record (New - Backend)

```java
public record AuthResult(
    String accessToken,
    String refreshToken,   // raw token value for cookie (NOT in body)
    LoginResponseDTO responseBody
) {}
```

### localStorage Keys (Frontend - After Migration)

| Key | Purpose | Set On |
|-----|---------|--------|
| `idUsuario` | User ID for UI | Login/Refresh |
| `nombre` | Display name | Login/Refresh |
| `email` | User email | Login/Refresh |
| `rol` | Role for guards | Login/Refresh |
| `urlImagen` | Avatar URL | Login/Refresh |
| `telefono` | Phone | Login/Refresh |
| `pais` | Country | Login/Refresh |
| `ciudad` | City | Login/Refresh |
| `direccion` | Address | Login/Refresh |
| `departamento` | Department | Login/Refresh |
| `codigoPostal` | Zip code | Login/Refresh |

**Removed keys**: `accessToken`, `refreshToken`, `expiraEn`

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Login sets correct cookie attributes

*For any* valid login request that produces an HTTP 200 response, the response SHALL contain a `Set-Cookie` header with a cookie named `refreshToken` that has `HttpOnly=true`, `Path=/auth/refresh`, `MaxAge=604800`, and `SameSite` and `Secure` values matching the active Spring profile.

**Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5**

### Property 2: Successful auth responses never expose refresh token in body

*For any* successful login or refresh response, the JSON response body SHALL NOT contain a non-null `refreshToken` field value.

**Validates: Requirements 1.8, 2.4**

### Property 3: Failed login produces no cookie

*For any* login request with invalid credentials, the HTTP response SHALL NOT contain a `Set-Cookie` header with a `refreshToken` cookie.

**Validates: Requirements 1.9**

### Property 4: Refresh round-trip via cookie

*For any* valid refresh token delivered via the `refreshToken` HttpOnly cookie, calling `POST /auth/refresh` (with no request body) SHALL return HTTP 200 with a new `Set-Cookie` header containing a rotated refresh token and an `Authorization` header containing a new Bearer access token.

**Validates: Requirements 2.1, 2.3, 2.5**

### Property 5: Logout invalidates cookie and server tokens

*For any* authenticated logout request (valid Bearer token in Authorization header), the response SHALL contain a `Set-Cookie` header with `refreshToken` cookie having `MaxAge=0`, AND subsequent refresh attempts with the previously valid refresh token SHALL fail with HTTP 401.

**Validates: Requirements 3.1, 3.2, 3.3, 3.6**

### Property 6: Frontend never persists sensitive tokens in client storage

*For any* successful login or refresh operation on the frontend, the `accessToken`, `refreshToken`, and `expiraEn` keys SHALL NOT be present in `localStorage` or `sessionStorage`, while user profile fields (idUsuario, nombre, email, rol, urlImagen, telefono, pais, ciudad, direccion, departamento, codigoPostal) SHALL be present in `localStorage`.

**Validates: Requirements 4.1, 4.2, 4.4, 6.1, 6.6**

### Property 7: withCredentials routing

*For any* HTTP request URL processed by the Auth_Interceptor, the request SHALL have `withCredentials: true` if and only if the URL contains `/auth/refresh` or `/auth/logout`. All other URLs SHALL NOT include `withCredentials: true`.

**Validates: Requirements 5.1, 5.2, 5.3, 6.2**

### Property 8: CORS origin filtering in production

*For any* list of configured allowed origins, when the production profile is active, the effective CORS origin list SHALL contain only origins starting with `https://`, and SHALL never contain the wildcard `*`.

**Validates: Requirements 7.2, 7.4**

## Error Handling

### Backend Error Scenarios

| Scenario | Response | Cookie Action |
|----------|----------|---------------|
| Login with invalid credentials | 401 Unauthorized | No cookie set |
| Refresh without cookie | 401 Unauthorized `"Refresh token ausente"` | No cookie set |
| Refresh with expired/revoked token | 401 Unauthorized `"Refresh token inválido"` | No cookie set |
| Logout without Authorization header | 401 Unauthorized (Spring Security filter) | No cookie deletion |
| Logout with blacklisted JWT | 401 Unauthorized (JwtAuthenticationFilter) | No cookie deletion |

### Frontend Error Scenarios

| Scenario | Action |
|----------|--------|
| Silent refresh on init fails (401 or network error) | Clear localStorage profile, reset Auth_State, redirect to `/login` |
| 401 on regular request → refresh attempt fails | Clear session, stop request pipeline (EMPTY) |
| 401 on regular request → refresh succeeds | Retry original request with new access token |
| Network error on logout | Clear local session anyway (best-effort server call) |

### Edge Cases

- **Cookie absent on refresh**: Backend returns 401 with descriptive message. Frontend treats as session expired.
- **Multiple tabs**: Each tab holds its own in-memory access token. Cookie is shared. Refresh from one tab rotates the token, potentially invalidating the other tab's cookie value. The 401→refresh flow handles this gracefully (one tab succeeds, others will get 401 and attempt their own refresh).
- **Page reload**: Access token is lost (memory). Silent refresh restores the session using the surviving HttpOnly cookie.

## Testing Strategy

### Unit Tests (Backend - JUnit 5 + MockMvc)

- Verify cookie attributes on login response (per-profile)
- Verify refresh reads from `@CookieValue` and rejects missing cookie
- Verify logout sets deletion cookie
- Verify `LoginResponseDTO` serializes without `refreshToken` field
- Verify `CookieUtil` builds correct cookies for prod and non-prod

### Unit Tests (Frontend - Vitest)

- Verify `AuthService.login()` stores only profile in localStorage, access token in signal
- Verify `AuthService.refresh()` sends no body and uses `withCredentials: true`
- Verify `AuthService.clearSession()` removes only profile keys
- Verify interceptor adds `withCredentials` only for refresh/logout URLs
- Verify interceptor reads token from signal, not localStorage
- Verify hydration calls silent refresh when profile exists but no token in memory
- Verify failed silent refresh clears session and redirects

### Property-Based Tests (Backend - jqwik)

Property-based testing is appropriate here because the cookie-building logic operates on varying inputs (token values, profile states) and must maintain invariants across all cases.

**Library**: jqwik (already used in the project — `.jqwik-database` file exists)

**Configuration**:
- Minimum 100 iterations per property
- Each test tagged with: `Feature: httponly-cookie-auth, Property {N}: {description}`

Tests to implement:
1. **Property 1**: Generate random token strings → verify `CookieUtil.buildRefreshCookie()` always produces correct attributes
2. **Property 2**: Generate random `LoginResponseDTO` instances → verify serialization never includes refreshToken
3. **Property 3**: Generate random invalid credentials → verify no Set-Cookie header in error response
4. **Property 4**: Create valid refresh tokens in DB, deliver via cookie → verify response always has new cookie + Authorization header
5. **Property 5**: Login then logout → verify subsequent refresh always fails
6. **Property 8**: Generate random origin lists → verify filtering rules

### Property-Based Tests (Frontend - fast-check)

**Library**: fast-check (standard PBT library for TypeScript/Vitest)

Tests to implement:
6. **Property 6**: Generate random login response payloads → verify localStorage never contains accessToken/refreshToken/expiraEn
7. **Property 7**: Generate random URLs → verify withCredentials is set iff URL contains /auth/refresh or /auth/logout

### Integration Tests

- End-to-end login → refresh → logout flow via HTTP (TestRestTemplate or WebTestClient)
- Verify cookie is automatically attached by the browser in cross-origin scenario (manual/E2E test with Playwright)
- Verify Angular proxy forwards `Set-Cookie` headers correctly in dev mode
