# Implementation Plan

## Overview

Fix the `refreshToken` cookie path mismatch by changing `COOKIE_PATH` from `"/auth/refresh"` to `"/api/auth/refresh"` in `CookieUtil.java`. Uses the bug condition methodology: write exploratory tests first to confirm the bug, then apply the fix, then verify all tests pass.

## Tasks

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Cookie Path Does Not Match Browser-Visible Refresh URL
  - **CRITICAL**: This test MUST FAIL on unfixed code — failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior — it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the cookie path mismatch
  - **Scoped PBT Approach**: Scope the property to the concrete failing case — assert `cookie.getPath()` equals `"/api/auth/refresh"` and that `"/api/auth/refresh".startsWith(cookie.getPath())` is true
  - Write a jqwik property-based test in `src/test/java/com/fesc/tiendaOnline/config/CookieUtilBugConditionTest.java`
  - Generate random token strings and boolean `isProd` values
  - For each combination: call `CookieUtil.buildRefreshCookie(token, isProd)` and assert `cookie.getPath()` equals `"/api/auth/refresh"`
  - Also assert `"/api/auth/refresh".startsWith(cookie.getPath())` to simulate browser path-matching
  - For `buildDeleteCookie(isProd)`: assert `cookie.getPath()` equals `"/api/auth/refresh"`
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS because `cookie.getPath()` returns `"/auth/refresh"` instead of `"/api/auth/refresh"` and `"/api/auth/refresh".startsWith("/auth/refresh")` is `false`
  - Document counterexamples: e.g., `buildRefreshCookie("abc123", true).getPath()` returns `"/auth/refresh"` — browser will not attach cookie to `/api/auth/refresh`
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 2.1_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Cookie Security Attributes Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - Write a jqwik property-based test in `src/test/java/com/fesc/tiendaOnline/config/CookieUtilPreservationTest.java`
  - Observe on UNFIXED code: `buildRefreshCookie("token", true)` → `HttpOnly=true`, `Secure=true`, `SameSite=None`, `MaxAge=604800`, `name=refreshToken`
  - Observe on UNFIXED code: `buildRefreshCookie("token", false)` → `HttpOnly=true`, `Secure=false`, `SameSite=Lax`, `MaxAge=604800`, `name=refreshToken`
  - Observe on UNFIXED code: `buildDeleteCookie(true)` → `HttpOnly=true`, `Secure=true`, `SameSite=None`, `MaxAge=0`, `name=refreshToken`, `value=""`
  - Observe on UNFIXED code: `buildDeleteCookie(false)` → `HttpOnly=true`, `Secure=false`, `SameSite=Lax`, `MaxAge=0`, `name=refreshToken`, `value=""`
  - Write property: for all random token strings and boolean isProd, `buildRefreshCookie(token, isProd)` always has `HttpOnly=true`, `name="refreshToken"`, `MaxAge=604800`, `value=token`
  - Write property: for all boolean isProd, `Secure == isProd` and `SameSite == (isProd ? "None" : "Lax")`
  - Write property: for all boolean isProd, `buildDeleteCookie(isProd)` has `HttpOnly=true`, `MaxAge=0`, `name="refreshToken"`, `value=""`
  - **DO NOT assert Path** in preservation tests — Path is the attribute being changed by the fix
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (confirms baseline security attributes to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 3. Fix cookie path mismatch in CookieUtil

  - [x] 3.1 Implement the fix
    - In `src/main/java/com/fesc/tiendaOnline/config/utilities/CookieUtil.java`, change the `COOKIE_PATH` constant from `"/auth/refresh"` to `"/api/auth/refresh"`
    - This is a single-line change: `private static final String COOKIE_PATH = "/api/auth/refresh";`
    - Both `buildRefreshCookie()` and `buildDeleteCookie()` use this constant, so both are fixed automatically
    - No other files need changes — frontend already sends to `/api/auth/refresh` with `withCredentials: true`
    - _Bug_Condition: isBugCondition(input) where cookiePath="/auth/refresh" AND browserRequestPath="/api/auth/refresh" — path does not match_
    - _Expected_Behavior: cookie.getPath() == "/api/auth/refresh" so "/api/auth/refresh".startsWith(cookie.getPath()) is true_
    - _Preservation: HttpOnly, Secure, SameSite, MaxAge, name, value attributes remain unchanged_
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3_

  - [x] 3.2 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Cookie Path Matches Browser-Visible Refresh URL
    - **IMPORTANT**: Re-run the SAME test from task 1 — do NOT write a new test
    - The test from task 1 encodes the expected behavior (`cookie.getPath() == "/api/auth/refresh"`)
    - When this test passes, it confirms the cookie path now matches what the browser sees
    - Run `CookieUtilBugConditionTest` — all property assertions should now succeed
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.3 Verify preservation tests still pass
    - **Property 2: Preservation** - Cookie Security Attributes Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
    - Run `CookieUtilPreservationTest` from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions to security attributes)
    - Confirm HttpOnly, Secure, SameSite, MaxAge, name, and value are all unchanged after fix
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 3.4 Update existing CookieUtilPropertyTest to reflect new path
    - Update assertions in `CookieUtilPropertyTest.java` from `"/auth/refresh"` to `"/api/auth/refresh"` so existing test suite aligns with the fix
    - Run the updated test to confirm it passes
    - _Requirements: 2.1, 3.1, 3.2_

- [x] 4. Checkpoint — Ensure all tests pass
  - Run `mvnw.cmd test` to execute the full test suite
  - Confirm `CookieUtilBugConditionTest` passes (bug is fixed)
  - Confirm `CookieUtilPreservationTest` passes (no regressions)
  - Confirm `CookieUtilPropertyTest` passes (updated path assertions)
  - Confirm all other existing tests still pass (no unintended side effects)
  - Ensure all tests pass, ask the user if questions arise.

## Task Dependency Graph

```json
{
  "waves": [
    ["1", "2"],
    ["3.1"],
    ["3.2", "3.3", "3.4"],
    ["4"]
  ]
}
```

## Notes

- The fix is a single constant change — minimal risk of regressions
- Existing `CookieUtilPropertyTest.java` asserts the OLD path (`/auth/refresh`) and must be updated in task 3.4
- jqwik is already configured in `pom.xml` and used in the project (see existing property tests)
- The exploration test (task 1) is expected to FAIL before the fix — this is intentional and confirms the bug
- The preservation test (task 2) is expected to PASS before the fix — this captures baseline behavior
