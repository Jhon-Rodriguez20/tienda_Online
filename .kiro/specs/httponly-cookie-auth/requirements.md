# Requirements Document

## Introduction

Migrate the token transport mechanism from localStorage to HttpOnly cookies. The Refresh Token will be stored exclusively in an HttpOnly cookie set by the backend, while the Access Token will be held only in memory on the frontend. This eliminates client-side JavaScript access to sensitive tokens, reducing XSS attack surface. No changes to JWT generation, RSA signatures, refresh token service logic, repositories, entities, revocation, or rotation logic.

## Glossary

- **Backend**: Spring Boot 4 REST API deployed on Render, serving endpoints under the `/auth` path prefix.
- **Frontend**: Angular 21 SPA deployed on Vercel, communicating with the Backend via HTTP.
- **Access_Token**: Short-lived JWT (15-min) used for Bearer authentication in the Authorization header.
- **Refresh_Token**: Long-lived opaque UUID token (7-day expiry) used to obtain a new Access_Token.
- **HttpOnly_Cookie**: An HTTP cookie with the HttpOnly flag set, preventing client-side JavaScript from reading its value.
- **Auth_Interceptor**: Angular functional HTTP interceptor that attaches Bearer tokens and handles 401 refresh flows.
- **AuthService_Backend**: Spring Boot service class responsible for login, refresh, and logout operations.
- **AuthController**: Spring Boot REST controller exposing `/auth/login`, `/auth/refresh`, and `/auth/logout` endpoints.
- **AuthService_Frontend**: Angular injectable service managing authentication state and HTTP calls for login, refresh, and logout.
- **Auth_State**: Angular Signals-based reactive state holding authentication data including the in-memory Access_Token.
- **SecurityConfig**: Spring Security configuration class managing CORS, session policy, and filter chain.
- **CORS_Configuration**: Cross-Origin Resource Sharing settings that control which origins can make credentialed requests to the Backend.

## Requirements

### Requirement 1: Backend Login Endpoint Cookie Delivery

**User Story:** As a frontend application, I want the login endpoint to deliver the Refresh Token via an HttpOnly cookie instead of the JSON response body, so that client-side JavaScript cannot access the Refresh Token.

#### Acceptance Criteria

1. WHEN a successful login request is processed and the AuthController returns an HTTP 200 response, THE AuthController SHALL set an HttpOnly cookie named `refreshToken` containing the Refresh_Token value in the HTTP response.
2. WHEN a successful login request is processed, THE AuthController SHALL configure the `refreshToken` cookie with the attribute HttpOnly equal to true.
3. WHEN a successful login request is processed, THE AuthController SHALL configure the `refreshToken` cookie with the attribute SameSite equal to Strict.
4. WHEN a successful login request is processed, THE AuthController SHALL configure the `refreshToken` cookie with the attribute Path equal to `/auth/refresh`.
5. WHEN a successful login request is processed, THE AuthController SHALL configure the `refreshToken` cookie with a MaxAge value equal to the Refresh_Token expiration duration of 7 days (604800 seconds).
6. WHILE the Backend is running with the production profile active, THE AuthController SHALL configure the `refreshToken` cookie with the attribute Secure equal to true.
7. WHILE the Backend is running with a non-production profile active, THE AuthController SHALL configure the `refreshToken` cookie with the attribute Secure equal to false.
8. WHEN a successful login request is processed, THE AuthController SHALL exclude the `refreshToken` field from the JSON response body by setting it to null or omitting it from the serialized LoginResponseDTO.
9. IF a login request fails due to invalid credentials or validation errors, THEN THE AuthController SHALL NOT set a `refreshToken` cookie in the HTTP response.

### Requirement 2: Backend Refresh Endpoint Cookie Consumption

**User Story:** As a frontend application, I want the refresh endpoint to read the Refresh Token from the HttpOnly cookie automatically, so that no manual token management is required on the client.

#### Acceptance Criteria

1. WHEN a refresh request is received, THE AuthController SHALL read the Refresh_Token value from the HttpOnly cookie named `refreshToken` instead of from the request body or headers.
2. IF a refresh request is received without a `refreshToken` cookie or with an empty `refreshToken` cookie value, THEN THE AuthController SHALL return HTTP 401 Unauthorized with an error message indicating the refresh token is missing.
3. WHEN a successful refresh is processed, THE AuthController SHALL set an updated HttpOnly cookie named `refreshToken` containing the new Refresh_Token value with the same attributes (HttpOnly=true, SameSite=Strict, Path=/auth/refresh, MaxAge=604800, Secure based on profile).
4. WHEN a successful refresh is processed, THE AuthController SHALL exclude the `refreshToken` field from the JSON response body.
5. WHEN a successful refresh is processed, THE AuthController SHALL continue returning the new Access_Token in the Authorization response header.
6. THE refresh endpoint SHALL NOT require a request body; the endpoint SHALL accept requests with an empty body or no body at all.
7. IF the Refresh_Token read from the cookie is expired or revoked, THEN THE AuthController SHALL return HTTP 401 Unauthorized using the existing service-level validation logic.

### Requirement 3: Backend Logout Endpoint Cookie Deletion

**User Story:** As a frontend application, I want the logout endpoint to delete the HttpOnly cookie, so that the Refresh Token is fully invalidated on both server and client.

#### Acceptance Criteria

1. WHEN a logout request is processed, THE AuthController SHALL delete the `refreshToken` cookie by setting its MaxAge to 0 and its Path to `/auth/refresh` in the response, matching the Path attribute used when the cookie was originally set.
2. WHEN a logout request is processed, THE AuthController SHALL configure the deletion cookie with the same HttpOnly, SameSite, and Secure attributes as defined in Requirement 1, so that the browser identifies and removes the correct cookie.
3. WHEN a logout request is processed, THE AuthController SHALL invalidate the Refresh_Token on the server side by revoking all refresh tokens for the user using existing revocation logic.
4. WHEN a logout request is processed, THE AuthController SHALL blacklist the Access_Token JTI in the Caffeine cache using existing blacklist logic.
5. IF the logout request does not contain a valid Authorization header with a Bearer token, THEN THE AuthController SHALL return HTTP 401 Unauthorized without attempting cookie deletion or token revocation.
6. WHEN a logout request is processed successfully, THE AuthController SHALL return HTTP 204 No Content with the cookie-deletion Set-Cookie header included in the response.

### Requirement 4: Frontend In-Memory Access Token Storage

**User Story:** As a developer, I want the Access Token stored exclusively in memory, so that it is not accessible via localStorage or sessionStorage and cannot be stolen through XSS.

#### Acceptance Criteria

1. WHEN a successful login or refresh response is received, THE AuthService_Frontend SHALL store the Access_Token exclusively in the Auth_State signal (in-memory) and SHALL NOT write the Access_Token, Refresh_Token, or expiraEn values to localStorage or sessionStorage.
2. THE AuthService_Frontend SHALL NOT read or write Access_Token, Refresh_Token, or expiraEn values from/to localStorage or sessionStorage at any point during the application lifecycle.
3. WHEN the browser tab is closed or refreshed, THE Auth_State signal SHALL reset to its default unauthenticated state, clearing the in-memory Access_Token.
4. WHEN a successful login or refresh response is received, THE AuthService_Frontend SHALL persist the following user profile fields to localStorage for UI session hydration: idUsuario, nombre, email, rol, urlImagen, telefono, pais, ciudad, direccion, departamento, codigoPostal.
5. WHEN the application initializes with user profile data present in localStorage but no Access_Token in the Auth_State signal, THE AuthService_Frontend SHALL issue a silent refresh request to the `/auth/refresh` endpoint with `withCredentials: true` so the browser sends the HttpOnly Refresh_Token cookie automatically.
6. IF the silent refresh request on application initialization fails (network error or non-2xx response), THEN THE AuthService_Frontend SHALL clear all user profile data from localStorage, reset the Auth_State signal to its default unauthenticated state, and redirect the user to the login page.
7. IF the silent refresh request on application initialization succeeds, THEN THE AuthService_Frontend SHALL store the received Access_Token in the Auth_State signal and update the localStorage user profile fields from the response, restoring an authenticated session without user interaction.

### Requirement 5: Frontend Interceptor Credentialed Requests

**User Story:** As a developer, I want the HTTP interceptor to send credentials (cookies) with requests to the backend, so that the HttpOnly cookie is automatically included in refresh and logout requests.

#### Acceptance Criteria

1. WHEN the Auth_Interceptor sends a request to a URL containing `/auth/refresh`, THE Auth_Interceptor SHALL include `withCredentials: true` in the request options.
2. WHEN the Auth_Interceptor sends a request to a URL containing `/auth/logout`, THE Auth_Interceptor SHALL include `withCredentials: true` in the request options.
3. WHEN the Auth_Interceptor sends an authenticated request to an endpoint that does not contain `/auth/refresh` or `/auth/logout`, THE Auth_Interceptor SHALL NOT include `withCredentials: true` in the request options.
4. WHILE Auth_State indicates the user is authenticated, WHEN the Auth_Interceptor processes a non-public request, THE Auth_Interceptor SHALL clone the request with the Access_Token from Auth_State as a Bearer token in the Authorization header.
5. WHEN a 401 response is received for a request whose URL does not contain `/auth/refresh`, THE Auth_Interceptor SHALL attempt a token refresh with `withCredentials: true` and, upon success, retry the original request with the new Access_Token from Auth_State.
6. IF the token refresh attempt triggered by a 401 response fails, THEN THE Auth_Interceptor SHALL clear the session via AuthService and cease emitting further values for that request.
7. THE Auth_Interceptor SHALL read the Access_Token exclusively from the Auth_State signal and never from cookies or localStorage.

### Requirement 6: Frontend Refresh Token Elimination

**User Story:** As a developer, I want the frontend to never handle the Refresh Token directly, so that it remains completely opaque and inaccessible to JavaScript.

#### Acceptance Criteria

1. THE AuthService_Frontend SHALL NOT store, read, or transmit the Refresh_Token value in any JavaScript variable, localStorage, sessionStorage, or request body.
2. WHEN the AuthService_Frontend initiates a token refresh, THE AuthService_Frontend SHALL send the request to POST /auth/refresh with no request body and with credentials included (withCredentials: true), relying on the browser to automatically attach the HttpOnly cookie.
3. THE Auth_State SHALL NOT include a `refreshToken` field in the AuthState interface.
4. THE LoginResponseDTO interface in the Frontend SHALL NOT include a `refreshToken` field.
5. THE RefreshRequestDTO interface in the Frontend SHALL be removed entirely since no request body is needed for refresh.
6. WHEN a login or refresh response is received, THE AuthService_Frontend SHALL NOT persist a `refreshToken` key in localStorage or sessionStorage.

### Requirement 7: CORS Configuration for Credentialed Requests

**User Story:** As a developer, I want CORS configured to support credentialed cross-origin requests, so that the HttpOnly cookie is sent with requests from the Angular frontend on a different origin.

#### Acceptance Criteria

1. THE SecurityConfig SHALL maintain `allowCredentials` set to true in the CORS_Configuration.
2. THE SecurityConfig SHALL use explicit allowed origins from the `app.cors.allowed-origins` property and SHALL reject any configuration that contains the wildcard `*` value, since browsers block credentialed requests with wildcard origins.
3. THE SecurityConfig SHALL include `Set-Cookie` in the list of exposed headers in the CORS_Configuration, in addition to the existing exposed headers (Authorization, Idempotency-Replayed, X-RateLimit-Remaining, X-RateLimit-Limit, Retry-After).
4. WHILE the Backend is running with the production profile, THE SecurityConfig SHALL filter the configured origins to include only those starting with `https://`, discarding any HTTP-scheme origins from the allowed list.
5. THE SecurityConfig SHALL set the CORS preflight cache duration (`maxAge`) to 3600 seconds.
6. THE SecurityConfig SHALL allow the HTTP methods GET, POST, PUT, DELETE, and OPTIONS in the CORS_Configuration.

### Requirement 8: Cross-Environment Compatibility

**User Story:** As a developer, I want the HttpOnly cookie mechanism to function correctly across local development, Docker, and production (Vercel + Render) deployments, so that authentication works in all environments.

#### Acceptance Criteria

1. WHILE running in local development (Angular dev server with proxy to localhost:8080), THE Backend SHALL set the `refreshToken` cookie with `Secure=false`, `HttpOnly=true`, `SameSite=Lax`, and `Path=/auth/refresh`.
2. WHILE running in Docker (same-origin via nginx reverse proxy) without the `prod` Spring profile active, THE Backend SHALL set the `refreshToken` cookie with `Secure=false`, `HttpOnly=true`, `SameSite=Lax`, and `Path=/auth/refresh`.
3. WHILE running in production (Frontend on Vercel, Backend on Render over HTTPS) with the `prod` Spring profile active, THE Backend SHALL set the `refreshToken` cookie with `Secure=true`, `HttpOnly=true`, `SameSite=None`, and `Path=/auth/refresh`.
4. THE Angular proxy configuration SHALL rewrite requests from `/api/**` to `http://localhost:8080` (stripping the `/api` prefix) and forward all `Set-Cookie` headers from the Backend response to the browser unmodified.
5. WHEN the Frontend sends a POST request to the refresh endpoint, THE Backend SHALL read the `refreshToken` value from the incoming cookie named `refreshToken` rather than from the request body.
6. IF the `refreshToken` cookie is absent or empty on a refresh request, THEN THE Backend SHALL reject the request with an HTTP 401 response containing an error message indicating the refresh token is missing.
7. WHILE running in production (cross-origin), THE CORS configuration SHALL include `Access-Control-Allow-Credentials: true` and the Vercel frontend origin in `Access-Control-Allow-Origin` so that the browser attaches the `refreshToken` cookie to cross-origin refresh requests.
