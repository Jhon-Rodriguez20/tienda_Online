# Implementation Plan: HttpOnly Cookie Auth

## Overview

Migrate the Refresh Token transport from localStorage to an HttpOnly cookie set by the Spring Boot backend, while keeping the Access Token exclusively in-memory on the Angular frontend. The implementation follows a backend-first approach: create the cookie utility, modify the auth controller/service, update CORS, then migrate the frontend to consume cookies and remove localStorage token storage.

## Tasks

- [x] 1. Backend: Create CookieUtil and AuthResult record
  - [x] 1.1 Create `CookieUtil` utility class in `com.fesc.tiendaOnline.config.utilities`
    - Create file `src/main/java/com/fesc/tiendaOnline/config/utilities/CookieUtil.java`
    - Implement `buildRefreshCookie(String tokenValue, boolean isProd)` returning `ResponseCookie` with: name=`refreshToken`, HttpOnly=true, Path=`/auth/refresh`, MaxAge=604800, SameSite=`None`(prod)/`Lax`(non-prod), Secure=true(prod)/false(non-prod)
    - Implement `buildDeleteCookie(boolean isProd)` returning `ResponseCookie` with MaxAge=0 and same attributes
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 3.1, 3.2, 8.1, 8.2, 8.3_

  - [x] 1.2 Create `AuthResult` record in `com.fesc.tiendaOnline.model.dto`
    - Create file `src/main/java/com/fesc/tiendaOnline/model/dto/AuthResult.java`
    - Define record with fields: `String accessToken`, `String refreshToken`, `LoginResponseDTO responseBody`
    - This record separates HTTP concerns (cookie/headers) from business logic in AuthService
    - _Requirements: 1.8, 2.4_

  - [x] 1.3 Write property test for CookieUtil (`CookieUtilPropertyTest.java`)
    - **Property 1: Login sets correct cookie attributes**
    - Create file `src/test/java/com/fesc/tiendaOnline/config/CookieUtilPropertyTest.java`
    - Use jqwik to generate random token strings; verify `buildRefreshCookie()` always produces HttpOnly=true, Path=/auth/refresh, MaxAge=604800, and environment-aware SameSite/Secure values
    - Verify `buildDeleteCookie()` always produces MaxAge=0 with same security attributes
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7**

- [x] 2. Backend: Modify AuthService to return AuthResult
  - [x] 2.1 Refactor `AuthService.java` to return `AuthResult` from login and refresh methods
    - Modify `login()` to return `AuthResult` instead of building `ResponseEntity` directly
    - Modify `refreshAccessToken()` to return `AuthResult` instead of building `ResponseEntity` directly
    - Set `refreshToken` field to `null` in the `LoginResponseDTO` body within `AuthResult`
    - Keep existing JWT generation, rotation, revocation, and validation logic unchanged
    - _Requirements: 1.8, 2.4, 2.5_

  - [x] 2.2 Update `LoginResponseDTO.java` to annotate `refreshToken` with `@JsonInclude(NON_NULL)`
    - Add `@JsonInclude(JsonInclude.Include.NON_NULL)` annotation to the `refreshToken` field
    - This ensures the field is omitted from JSON serialization when null
    - _Requirements: 1.8, 2.4_

  - [ ]* 2.3 Write property test for LoginResponseDTO serialization
    - **Property 2: Successful auth responses never expose refresh token in body**
    - Create file `src/test/java/com/fesc/tiendaOnline/controller/LoginResponseDTOPropertyTest.java`
    - Use jqwik to generate random LoginResponseDTO instances with refreshToken=null; verify JSON serialization never contains a `refreshToken` key
    - **Validates: Requirements 1.8, 2.4**

- [x] 3. Backend: Modify AuthController for cookie-based auth
  - [x] 3.1 Modify `AuthController.java` login endpoint to set HttpOnly cookie
    - Inject an `isProd` boolean derived from the active Spring profile (e.g., `@Value("${spring.profiles.active:}")` or `Environment` bean)
    - Change login to call `authService.login()` which returns `AuthResult`
    - Build `ResponseCookie` via `CookieUtil.buildRefreshCookie(result.refreshToken(), isProd)`
    - Add `Set-Cookie` header and `Authorization: Bearer <AT>` header to the response
    - Return body with `refreshToken=null`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9_

  - [x] 3.2 Modify `AuthController.java` refresh endpoint to read from cookie
    - Change method signature to accept `@CookieValue(name = "refreshToken", required = false) String refreshToken` instead of `@RequestBody RefreshRequestDTO`
    - Return 401 if cookie is null or blank with message "Refresh token ausente"
    - Call `authService.refreshAccessToken(refreshToken)` and build response with new cookie + Authorization header
    - Remove request body requirement entirely
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 8.5, 8.6_

  - [x] 3.3 Modify `AuthController.java` logout endpoint to delete cookie
    - After revoking tokens and blacklisting JTI (existing logic), build deletion cookie via `CookieUtil.buildDeleteCookie(isProd)`
    - Add `Set-Cookie` header with deletion cookie to the 204 No Content response
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [ ]* 3.4 Write property test for failed login producing no cookie
    - **Property 3: Failed login produces no cookie**
    - Create file `src/test/java/com/fesc/tiendaOnline/controller/AuthControllerCookiePropertyTest.java`
    - Use jqwik + MockMvc to generate random invalid credentials; verify response never contains `Set-Cookie` header with `refreshToken`
    - **Validates: Requirements 1.9**

  - [ ]* 3.5 Write property test for refresh round-trip via cookie
    - **Property 4: Refresh round-trip via cookie**
    - In `AuthControllerCookiePropertyTest.java`, add property that creates valid refresh tokens and sends them via cookie; verify response always has new Set-Cookie + Authorization header
    - **Validates: Requirements 2.1, 2.3, 2.5**

  - [ ]* 3.6 Write property test for logout invalidating cookie and tokens
    - **Property 5: Logout invalidates cookie and server tokens**
    - In `AuthControllerCookiePropertyTest.java`, add property for authenticated logout; verify response has Set-Cookie with MaxAge=0 and subsequent refresh attempts fail with 401
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.6**

- [x] 4. Backend: Update SecurityConfig CORS
  - [x] 4.1 Modify `SecurityConfig.java` to expose `Set-Cookie` header and enforce CORS rules
    - Add `"Set-Cookie"` to the `exposedHeaders` list in CORS configuration
    - Ensure `allowCredentials` is set to true
    - Ensure explicit origins from `app.cors.allowed-origins` property are used (no wildcard `*`)
    - For prod profile, filter origins to include only those starting with `https://`
    - Set preflight cache `maxAge` to 3600 seconds
    - Allow HTTP methods: GET, POST, PUT, DELETE, OPTIONS
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 8.7_

  - [x] 4.2 Write property test for CORS origin filtering in production
    - **Property 8: CORS origin filtering in production**
    - Create file `src/test/java/com/fesc/tiendaOnline/config/SecurityConfigCorsPropertyTest.java`
    - Use jqwik to generate random origin lists; verify prod profile filtering produces only `https://` origins and never contains `*`
    - **Validates: Requirements 7.2, 7.4**

- [x] 5. Checkpoint - Backend compilation and tests
  - Ensure all backend code compiles successfully with `mvnw compile`
  - Run existing tests with `mvnw test` to verify no regressions
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Frontend: Update models and AuthState
  - [x] 6.1 Update `auth.models.ts` to remove `refreshToken` from DTOs
    - Remove `refreshToken` field from `LoginResponseDTO` interface
    - Remove `RefreshRequestDTO` interface entirely (no request body needed for refresh)
    - _Requirements: 6.4, 6.5_

  - [x] 6.2 Update `auth.state.ts` to remove `refreshToken` and `expiraEn` from AuthState
    - Remove `refreshToken` field from the `AuthState` interface
    - Remove `expiraEn` field from the `AuthState` interface
    - Keep `accessToken` as in-memory-only field in the signal
    - _Requirements: 6.3, 4.1, 4.2_

- [x] 7. Frontend: Modify AuthService for cookie-based auth
  - [x] 7.1 Modify `auth.service.ts` login method
    - After successful login, store only user profile fields in localStorage (idUsuario, nombre, email, rol, urlImagen, telefono, pais, ciudad, direccion, departamento, codigoPostal)
    - Store Access Token exclusively in Auth_State signal (in-memory)
    - Remove any code writing `accessToken`, `refreshToken`, or `expiraEn` to localStorage
    - _Requirements: 4.1, 4.2, 4.4, 6.1, 6.6_

  - [x] 7.2 Modify `auth.service.ts` refresh method
    - Change refresh to send `POST /auth/refresh` with `null` body and `{ withCredentials: true }`
    - Read new Access Token from `Authorization` response header
    - Store new Access Token in Auth_State signal only
    - Remove `RefreshRequestDTO` usage
    - _Requirements: 2.6, 4.1, 5.1, 6.1, 6.2_

  - [x] 7.3 Modify `auth.service.ts` logout method
    - Send logout request with `{ withCredentials: true }` so browser attaches cookie
    - Clear Auth_State signal and all localStorage profile keys on success
    - On network error, clear local session anyway (best-effort server call)
    - _Requirements: 3.6, 5.2_

  - [x] 7.4 Implement silent refresh hydration in `auth.service.ts`
    - On app initialization, if profile data exists in localStorage but no Access Token in Auth_State signal, issue silent refresh
    - On success: store Access Token in signal, update localStorage profile fields
    - On failure (401 or network error): clear localStorage profile, reset Auth_State, redirect to `/login`
    - _Requirements: 4.5, 4.6, 4.7_

  - [x] 7.5 Remove localStorage cleanup of token keys from `clearSession()`
    - Ensure `clearSession()` only removes profile keys from localStorage
    - Verify `accessToken`, `refreshToken`, `expiraEn` keys are never referenced
    - _Requirements: 4.2, 6.1, 6.6_

  - [x] 7.6 Write property test for frontend never persisting sensitive tokens
    - **Property 6: Frontend never persists sensitive tokens in client storage**
    - Create file `src/app/core/services/auth/auth.service.pbt.spec.ts` in the frontend project
    - Install `fast-check` if not present; use it to generate random login response payloads
    - Verify that after login/refresh, localStorage never contains `accessToken`, `refreshToken`, or `expiraEn` keys, while profile keys are present
    - **Validates: Requirements 4.1, 4.2, 4.4, 6.1, 6.6**

- [x] 8. Frontend: Modify Auth Interceptor
  - [x] 8.1 Modify `auth.interceptor.ts` to add `withCredentials` routing
    - Add `withCredentials: true` to requests where URL contains `/auth/refresh` or `/auth/logout`
    - Do NOT add `withCredentials` to any other request URLs
    - Read Access Token exclusively from Auth_State signal (not from localStorage or cookies)
    - On 401 response (for non-refresh URLs): attempt refresh with `withCredentials: true`, retry original request on success, clear session on failure
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

  - [ ]* 8.2 Write property test for withCredentials routing
    - **Property 7: withCredentials routing**
    - Create file `src/app/core/interceptors/auth.interceptor.pbt.spec.ts` in the frontend project
    - Use `fast-check` to generate random URL strings; verify withCredentials is set iff URL contains `/auth/refresh` or `/auth/logout`
    - **Validates: Requirements 5.1, 5.2, 5.3, 6.2**

- [x] 9. Frontend: Update existing unit tests
  - [x] 9.1 Update `auth.service.http.spec.ts` and other auth test files
    - Update tests to reflect that refresh sends no body and uses `withCredentials: true`
    - Update login tests to verify no token keys written to localStorage
    - Remove references to `RefreshRequestDTO`
    - Verify hydration silent-refresh behavior
    - _Requirements: 4.1, 4.2, 6.2, 6.5_

  - [x] 9.2 Update `auth.interceptor.spec.ts`
    - Update tests for `withCredentials` logic: verify it's added only for refresh/logout URLs
    - Verify interceptor reads token from Auth_State signal, not localStorage
    - Test 401→refresh→retry flow with `withCredentials: true`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.7_

  - [x] 9.3 Update `auth.state.spec.ts`
    - Remove tests referencing `refreshToken` or `expiraEn` in AuthState
    - Add tests verifying the new AuthState interface shape
    - _Requirements: 6.3_

- [x] 10. Final Checkpoint - Full integration verification
  - Run backend tests with `mvnw test` to verify all backend changes
  - Run frontend tests with `npx vitest --run` to verify all frontend changes
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties using jqwik (backend) and fast-check (frontend)
- Unit tests validate specific examples and edge cases
- Backend tasks (1-5) should be completed before frontend tasks (6-10) since frontend depends on the new API behavior
- The `RefreshRequestDTO.java` on the backend can be left in place (unused) or removed — it's no longer referenced by the controller

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "2.1", "2.2"] },
    { "id": 2, "tasks": ["2.3", "3.1", "3.2", "3.3", "4.1"] },
    { "id": 3, "tasks": ["3.4", "3.5", "3.6", "4.2"] },
    { "id": 4, "tasks": ["6.1", "6.2"] },
    { "id": 5, "tasks": ["7.1", "7.2", "7.3", "7.4", "7.5"] },
    { "id": 6, "tasks": ["7.6", "8.1"] },
    { "id": 7, "tasks": ["8.2", "9.1", "9.2", "9.3"] }
  ]
}
```
