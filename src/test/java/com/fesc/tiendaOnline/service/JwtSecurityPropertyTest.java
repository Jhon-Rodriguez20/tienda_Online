package com.fesc.tiendaOnline.service;

import com.fesc.tiendaOnline.exception.UnauthorizedException;
import com.fesc.tiendaOnline.model.dto.LoginResponseDTO;
import com.fesc.tiendaOnline.model.entity.RefreshTokenEntity;
import com.fesc.tiendaOnline.model.entity.UsuarioEntity;
import com.fesc.tiendaOnline.model.entity.UsuarioRolEntity;
import com.fesc.tiendaOnline.repository.RefreshTokenRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for JWT security: refresh token rotation, JwtBlacklist,
 * and JwtService startup expiration validation.
 *
 * Covers task 18.8 (Properties 25–27).
 * No Spring context needed — all tests are pure unit tests.
 */
class JwtSecurityPropertyTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private UsuarioEntity buildUsuario(UUID idUsuario) {
        UsuarioRolEntity rol = new UsuarioRolEntity();
        rol.setRolUsuario("ROLE_USER");

        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setIdUsuario(idUsuario);
        usuario.setNombre("Test User");
        usuario.setEmail("test@example.com");
        usuario.setUrlImagen("https://example.com/img.png");
        usuario.setUsuarioRol(rol);
        return usuario;
    }

    private RefreshTokenEntity buildValidRefreshToken(String tokenValue, UsuarioEntity usuario) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setToken(tokenValue);
        entity.setUsuario(usuario);
        entity.setFechaExpiracion(LocalDateTime.now().plusDays(7));
        entity.setRevocado(false);
        return entity;
    }

    // -----------------------------------------------------------------------
    // Property 25 — Refresh token rotation
    // Validates: Requirements 16.3
    // -----------------------------------------------------------------------

    /**
     * Property 25: Para cualquier refresh token válido, tras {@code refreshAccessToken()},
     * el token anterior está revocado (revokeAllByUsuarioIdUsuario llamado) y se emite
     * un nuevo par (nuevo token guardado, header Authorization presente).
     *
     * <p><b>Validates: Requirements 16.3</b></p>
     */
    @Property(tries = 50)
    void property25_refreshTokenRotation(@ForAll("validRefreshTokenValues") String tokenValue)
            throws Exception {

        UUID userId = UUID.randomUUID();
        UsuarioEntity usuario = buildUsuario(userId);
        RefreshTokenEntity oldToken = buildValidRefreshToken(tokenValue, usuario);

        // Mock repository
        RefreshTokenRepository refreshTokenRepo = Mockito.mock(RefreshTokenRepository.class);
        Mockito.when(refreshTokenRepo.findByToken(tokenValue)).thenReturn(Optional.of(oldToken));

        // Capture the new refresh token saved
        ArgumentCaptor<RefreshTokenEntity> savedTokenCaptor =
                ArgumentCaptor.forClass(RefreshTokenEntity.class);
        Mockito.when(refreshTokenRepo.save(savedTokenCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        // Mock JwtService
        JwtService jwtService = Mockito.mock(JwtService.class);
        Mockito.when(jwtService.generateToken(Mockito.any(UsuarioEntity.class)))
               .thenReturn("mocked.access.token");
        Mockito.when(jwtService.getExpirationTimeToken()).thenReturn(900L);

        // Build AuthService with mocked dependencies (other deps not needed for this path)
        AuthService authService = new AuthService(
                Mockito.mock(com.fesc.tiendaOnline.service.UsuarioValidationService.class),
                jwtService,
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(),
                refreshTokenRepo,
                Mockito.mock(JwtBlacklist.class)
        );

        // Act
        ResponseEntity<LoginResponseDTO> response = authService.refreshAccessToken(tokenValue);

        // Assert 1: revokeAllByUsuarioIdUsuario was called with the correct user ID
        Mockito.verify(refreshTokenRepo).revokeAllByUsuarioIdUsuario(userId);

        // Assert 2: a new RefreshTokenEntity was saved with a different token value
        RefreshTokenEntity savedToken = savedTokenCaptor.getValue();
        assertThat(savedToken).isNotNull();
        assertThat(savedToken.getToken())
                .as("New refresh token must differ from the old one")
                .isNotEqualTo(tokenValue);

        // Assert 3: response body contains the new refresh token
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRefreshToken())
                .as("Response body must contain the new refresh token")
                .isNotNull()
                .isNotBlank()
                .isEqualTo(savedToken.getToken());

        // Assert 4: Authorization header contains a Bearer token
        String authHeader = response.getHeaders().getFirst("Authorization");
        assertThat(authHeader)
                .as("Authorization header must be present and start with 'Bearer '")
                .isNotNull()
                .startsWith("Bearer ");
    }

    @Property(tries = 50)
    void property25_revokedTokenThrowsUnauthorized(@ForAll("validRefreshTokenValues") String tokenValue) {
        UUID userId = UUID.randomUUID();
        UsuarioEntity usuario = buildUsuario(userId);

        RefreshTokenEntity revokedToken = buildValidRefreshToken(tokenValue, usuario);
        revokedToken.setRevocado(true);

        RefreshTokenRepository refreshTokenRepo = Mockito.mock(RefreshTokenRepository.class);
        Mockito.when(refreshTokenRepo.findByToken(tokenValue)).thenReturn(Optional.of(revokedToken));

        AuthService authService = new AuthService(
                Mockito.mock(com.fesc.tiendaOnline.service.UsuarioValidationService.class),
                Mockito.mock(JwtService.class),
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(),
                refreshTokenRepo,
                Mockito.mock(JwtBlacklist.class)
        );

        assertThatThrownBy(() -> authService.refreshAccessToken(tokenValue))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Provide
    Arbitrary<String> validRefreshTokenValues() {
        // Generate UUID v4 strings as refresh token values
        return Arbitraries.create(() -> UUID.randomUUID().toString());
    }

    // -----------------------------------------------------------------------
    // Property 26 — JwtBlacklist invalidates tokens after logout
    // Validates: Requirements 16.5, 16.6
    // -----------------------------------------------------------------------

    /**
     * Property 26: Para cualquier JTI, tras llamar {@code add(jti)}, {@code isBlacklisted(jti)}
     * retorna {@code true}; antes de añadirlo retorna {@code false}; y un JTI diferente
     * no añadido permanece en {@code false}.
     *
     * <p><b>Validates: Requirements 16.5, 16.6</b></p>
     */
    @Property(tries = 100)
    void property26_blacklistInvalidatesJti(@ForAll("jtiValues") String jti) {
        // Create a fresh JwtBlacklist instance directly (no Spring)
        JwtBlacklist blacklist = new JwtBlacklist(100_000L);

        // 1. Before adding: isBlacklisted must be false
        assertThat(blacklist.isBlacklisted(jti))
                .as("JTI '%s' should NOT be blacklisted before add()", jti)
                .isFalse();

        // 2. Add the JTI
        blacklist.add(jti);

        // 3. After adding: isBlacklisted must be true
        assertThat(blacklist.isBlacklisted(jti))
                .as("JTI '%s' SHOULD be blacklisted after add()", jti)
                .isTrue();

        // 4. A different JTI (not added) must still be false
        String otherJti = jti + "_other_" + UUID.randomUUID();
        assertThat(blacklist.isBlacklisted(otherJti))
                .as("Different JTI '%s' should NOT be blacklisted", otherJti)
                .isFalse();
    }

    @Provide
    Arbitrary<String> jtiValues() {
        // Mix of UUID v4 strings and alphanumeric strings
        Arbitrary<String> uuids = Arbitraries.create(() -> UUID.randomUUID().toString());
        Arbitrary<String> alphanumeric = Arbitraries.strings()
                .alpha()
                .ofMinLength(8)
                .ofMaxLength(64);
        return Arbitraries.oneOf(uuids, alphanumeric);
    }

    // -----------------------------------------------------------------------
    // Property 27 — JwtService startup validation: expiration > 900_000L must throw
    // Validates: Requirements 18.1
    // -----------------------------------------------------------------------

    /**
     * Property 27: Para cualquier valor {@code expiration > 900_000L}, el método
     * {@code loadKeys()} de {@code JwtService} debe lanzar {@link IllegalStateException}
     * con el mensaje {@code "jwt.expiration no puede superar 15 minutos (900000 ms)"}.
     *
     * The expiration guard runs FIRST in {@code loadKeys()}, before any PEM file access,
     * so we only need to verify the exception is thrown — no real key files are needed.
     *
     * <p><b>Validates: Requirements 18.1</b></p>
     */
    @Property(tries = 100)
    void property27_expirationOverLimitThrowsOnStartup(
            @ForAll @LongRange(min = 900_001L, max = 10_000_000L) long expirationMs)
            throws Exception {

        JwtService jwtService = instantiateJwtServiceWithExpiration(expirationMs);

        // loadKeys() should throw before reaching PEM loading because the guard is first
        assertThatThrownBy(() -> invokeLoadKeys(jwtService))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.expiration no puede superar 15 minutos (900000 ms)");
    }

    /**
     * Boundary check: {@code expiration = 900_000L} must NOT throw — this is exactly at the limit.
     * Note: this IS the boundary value test to confirm the guard is {@code >}, not {@code >=}.
     *
     * We cannot invoke {@code loadKeys()} all the way through without real PEM files here,
     * but we can verify no IllegalStateException is thrown by the expiration guard itself
     * by checking that the exception message does NOT contain the expiration message if
     * any exception is thrown (it would be an IOException about missing keys instead).
     */
    @Example
    void property27_expirationAtExactLimitDoesNotThrowExpirationError() throws Exception {
        JwtService jwtService = instantiateJwtServiceWithExpiration(900_000L);

        try {
            invokeLoadKeys(jwtService);
            // If it somehow succeeds (keys found), that is also fine
        } catch (Exception e) {
            // The only acceptable exception here is NOT the expiration IllegalStateException
            // (it could be IOException about missing PEM, NullPointerException on null resource, etc.)
            assertThat(e)
                    .as("expiration=900000 must NOT cause the expiration guard to throw, " +
                        "but got: " + e.getMessage())
                    .satisfies(ex ->
                        assertThat(ex.getMessage() == null ||
                                   !ex.getMessage().contains("jwt.expiration no puede superar"))
                        .as("Expiration guard must not fire at exactly 900000 ms")
                        .isTrue()
                    );
        }
    }

    // -----------------------------------------------------------------------
    // Reflection helpers for Property 27
    // -----------------------------------------------------------------------

    /**
     * Creates a {@link JwtService} instance without Spring and sets the {@code expiration} field
     * via reflection to the given value. All other fields remain null/default.
     */
    private JwtService instantiateJwtServiceWithExpiration(long expirationMs) throws Exception {
        // Use the default no-arg constructor (Spring beans have one at the bytecode level)
        JwtService service = createJwtServiceInstance();
        Field expirationField = JwtService.class.getDeclaredField("expiration");
        expirationField.setAccessible(true);
        expirationField.set(service, expirationMs);
        return service;
    }

    /**
     * Instantiates {@link JwtService} without Spring context.
     * The class has only {@code @Service} and no required constructor args,
     * so we can use the default constructor.
     */
    private JwtService createJwtServiceInstance() throws Exception {
        // JwtService has no explicit constructor, so the default no-arg constructor is used
        java.lang.reflect.Constructor<JwtService> ctor =
                JwtService.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    /**
     * Invokes the {@code loadKeys()} method on the given {@link JwtService} instance via reflection.
     * Unwraps {@link java.lang.reflect.InvocationTargetException} to expose the real cause.
     */
    private void invokeLoadKeys(JwtService service) throws Exception {
        Method loadKeys = JwtService.class.getDeclaredMethod("loadKeys");
        loadKeys.setAccessible(true);
        try {
            loadKeys.invoke(service);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(cause);
        }
    }
}
