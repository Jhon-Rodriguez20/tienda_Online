# Bugfix Requirements Document

## Introduction

The HttpOnly `refreshToken` cookie does not persist across page reloads, causing users to lose their session. The root cause is a **cookie path mismatch**: the backend sets the cookie with `Path=/auth/refresh`, but the browser sees all refresh requests going to `/api/auth/refresh` (because the frontend uses `/api` as its base URL prefix). Since the browser's cookie path-matching requires the request path to start with the cookie's `Path` attribute, the cookie is never attached to subsequent refresh requests after page reload, causing silent refresh to fail with a 401.

This affects both development (Angular proxy) and production (Vercel rewrite) environments.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN the backend sets the `refreshToken` cookie with `Path=/auth/refresh` and the frontend subsequently sends a refresh request to `/api/auth/refresh`, THEN the browser does not attach the cookie because the request path `/api/auth/refresh` does not start with the cookie path `/auth/refresh`

1.2 WHEN the user reloads the page and the frontend attempts a silent refresh to `/api/auth/refresh` with `withCredentials: true`, THEN the browser does not include the `refreshToken` cookie in the request because the stored cookie path `/auth/refresh` does not match the request path `/api/auth/refresh`

1.3 WHEN the silent refresh request arrives at the backend without the `refreshToken` cookie due to the path mismatch, THEN the backend returns HTTP 401 "Refresh token ausente" and the frontend clears the session and redirects to login

### Expected Behavior (Correct)

2.1 WHEN the backend sets the `refreshToken` cookie, THEN the cookie `Path` attribute SHALL match the path that the browser observes for refresh requests, ensuring the browser attaches the cookie on subsequent requests to the refresh endpoint

2.2 WHEN the user reloads the page and the frontend attempts a silent refresh with `withCredentials: true`, THEN the browser SHALL include the `refreshToken` cookie in the request because the cookie path matches the browser-visible request path

2.3 WHEN the silent refresh request arrives at the backend with the `refreshToken` cookie attached, THEN the backend SHALL successfully process the refresh and return a new access token and a rotated refresh token cookie, preserving the user's session across page reloads

### Unchanged Behavior (Regression Prevention)

3.1 WHEN the cookie is set on a successful login, THEN the system SHALL CONTINUE TO set `HttpOnly=true`, `MaxAge=604800`, `SameSite` based on environment, and `Secure` based on environment

3.2 WHEN the cookie is set on a successful refresh, THEN the system SHALL CONTINUE TO set `HttpOnly=true`, `MaxAge=604800`, `SameSite` based on environment, and `Secure` based on environment

3.3 WHEN a logout request is processed, THEN the system SHALL CONTINUE TO delete the cookie by setting `MaxAge=0` with the correct path matching the one used for setting

3.4 WHEN the frontend sends a refresh request, THEN the system SHALL CONTINUE TO send the request with `withCredentials: true` and no request body

3.5 WHEN the backend receives a refresh request without a valid cookie, THEN the system SHALL CONTINUE TO return HTTP 401 "Refresh token ausente"

3.6 WHEN the backend is running in production mode, THEN the CORS configuration SHALL CONTINUE TO include `Access-Control-Allow-Credentials: true` and explicit allowed origins
