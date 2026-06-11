package com.fesc.tiendaOnline.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fesc.tiendaOnline.model.dto.LoginRequestDTO;
import com.fesc.tiendaOnline.model.dto.LoginResponseDTO;
import com.fesc.tiendaOnline.model.dto.RefreshRequestDTO;
import com.fesc.tiendaOnline.service.AuthService;
import com.fesc.tiendaOnline.service.JwtService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        return authService.login(loginRequest);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(@Valid @RequestBody RefreshRequestDTO request) {
        return authService.refreshAccessToken(request.getRefreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7); // strip "Bearer "
        String jti = jwtService.extractJti(token);
        UUID idUsuario = jwtService.extractIdUsuario(token);
        authService.logout(jti, idUsuario);
        return ResponseEntity.noContent().build();
    }
}
