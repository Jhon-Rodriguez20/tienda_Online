package com.fesc.tiendaOnline.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fesc.tiendaOnline.component.JwtBlacklist;
import com.fesc.tiendaOnline.exception.UnauthorizedException;
import com.fesc.tiendaOnline.model.dto.AuthResult;
import com.fesc.tiendaOnline.model.dto.LoginRequestDTO;
import com.fesc.tiendaOnline.model.dto.LoginResponseDTO;
import com.fesc.tiendaOnline.model.entity.RefreshTokenEntity;
import com.fesc.tiendaOnline.model.entity.UsuarioEntity;
import com.fesc.tiendaOnline.repository.RefreshTokenRepository;

@Service
public class AuthService {

    private final UsuarioValidationService usuarioValidationService;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtBlacklist jwtBlacklist;

    public AuthService(UsuarioValidationService usuarioValidationService,
            JwtService jwtService,
            BCryptPasswordEncoder passwordEncoder,
            RefreshTokenRepository refreshTokenRepository,
            JwtBlacklist jwtBlacklist) {
        this.usuarioValidationService = usuarioValidationService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtBlacklist = jwtBlacklist;
    }

    @Transactional
    public void logout(String jti, UUID idUsuario) {
        jwtBlacklist.add(jti);
        refreshTokenRepository.revokeAllByUsuarioIdUsuario(idUsuario);
    }

    @Transactional
    public AuthResult refreshAccessToken(String refreshTokenValue) {
        RefreshTokenEntity refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new UnauthorizedException("Refresh token inválido"));

        if (refreshToken.isRevocado()) {
            throw new UnauthorizedException("Refresh token inválido");
        }
        if (refreshToken.getFechaExpiracion().isBefore(java.time.LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token inválido");
        }

        UsuarioEntity usuario = refreshToken.getUsuario();

        // Revocar todos los tokens anteriores del usuario
        refreshTokenRepository.revokeAllByUsuarioIdUsuario(usuario.getIdUsuario());

        // Crear nuevo refresh token
        RefreshTokenEntity newRefreshToken = new RefreshTokenEntity();
        newRefreshToken.setToken(UUID.randomUUID().toString());
        newRefreshToken.setUsuario(usuario);
        newRefreshToken.setFechaExpiracion(java.time.LocalDateTime.now().plusDays(7));
        newRefreshToken.setRevocado(false);
        refreshTokenRepository.save(newRefreshToken);

        // Generar nuevo access token
        String newAccessToken = jwtService.generateToken(usuario);

        LoginResponseDTO response = new LoginResponseDTO();
        response.setIdUsuario(usuario.getIdUsuario());
        response.setNombre(usuario.getNombre());
        response.setEmail(usuario.getEmail());
        response.setRol(usuario.getUsuarioRol().getRolUsuario());
        response.setUrlImagen(usuario.getUrlImagen());
        response.setExpiraEn(jwtService.getExpirationTimeToken());
        response.setRefreshToken(null);

        return new AuthResult(newAccessToken, newRefreshToken.getToken(), response);
    }

    @Transactional
    public AuthResult login(LoginRequestDTO loginRequest) {
        UsuarioEntity usuario = usuarioValidationService.obtenerUsuarioPorEmailConRol(loginRequest.getEmail());
        usuarioValidationService.validarUsuarioActivo(usuario,
                "Usuario no activo. Debes verificar tu cuenta primero");

        if (!passwordEncoder.matches(loginRequest.getContrasena(), usuario.getContrasenaEncp())) {
            throw new UnauthorizedException("Credenciales invalidas");
        }

        String token = jwtService.generateToken(usuario);

        // Revocar todos los refresh tokens anteriores del usuario
        refreshTokenRepository.revokeAllByUsuarioIdUsuario(usuario.getIdUsuario());

        // Crear y persistir el nuevo refresh token
        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity();
        refreshTokenEntity.setToken(UUID.randomUUID().toString());
        refreshTokenEntity.setUsuario(usuario);
        refreshTokenEntity.setFechaExpiracion(LocalDateTime.now().plusDays(7));
        refreshTokenEntity.setRevocado(false);
        refreshTokenRepository.save(refreshTokenEntity);

        LoginResponseDTO response = new LoginResponseDTO();
        response.setIdUsuario(usuario.getIdUsuario());
        response.setNombre(usuario.getNombre());
        response.setEmail(usuario.getEmail());
        response.setRol(usuario.getUsuarioRol().getRolUsuario());
        response.setUrlImagen(usuario.getUrlImagen());
        response.setTelefono(usuario.getTelefono());
        response.setPais(usuario.getPais());
        response.setCiudad(usuario.getCiudad());
        response.setDepartamento(usuario.getDepartamento());
        response.setDireccion(usuario.getDireccion());
        response.setCodigoPostal(usuario.getCodigoPostal() != null ? usuario.getCodigoPostal() : "No disponible");
        response.setExpiraEn(jwtService.getExpirationTimeToken());
        response.setRefreshToken(null);

        return new AuthResult(token, refreshTokenEntity.getToken(), response);
    }
}
