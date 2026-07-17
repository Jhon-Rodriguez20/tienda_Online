package com.fesc.tiendaOnline.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fesc.tiendaOnline.config.utilities.CookieUtil;
import com.fesc.tiendaOnline.exception.UnauthorizedException;
import com.fesc.tiendaOnline.model.dto.AuthResult;
import com.fesc.tiendaOnline.model.dto.LoginRequestDTO;
import com.fesc.tiendaOnline.model.dto.LoginResponseDTO;
import com.fesc.tiendaOnline.service.AuthService;
import com.fesc.tiendaOnline.service.JwtService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final boolean isProd;

    public AuthController(AuthService authService, JwtService jwtService,
            @Value("${spring.profiles.active:}") String activeProfiles) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.isProd = activeProfiles.contains("prod");
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        AuthResult result = authService.login(loginRequest);
        ResponseCookie cookie = CookieUtil.buildRefreshCookie(result.refreshToken(), isProd);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + result.accessToken())
                .body(result.responseBody());
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
        String token = authHeader.substring(7); // strip "Bearer "
        String jti = jwtService.extractJti(token);
        UUID idUsuario = jwtService.extractIdUsuario(token);
        authService.logout(jti, idUsuario);
        ResponseCookie deleteCookie = CookieUtil.buildDeleteCookie(isProd);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .build();
    }
}
