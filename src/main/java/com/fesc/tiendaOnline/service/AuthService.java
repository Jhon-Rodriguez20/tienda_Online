package com.fesc.tiendaOnline.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.fesc.tiendaOnline.exception.UnauthorizedException;
import com.fesc.tiendaOnline.model.dto.LoginRequestDTO;
import com.fesc.tiendaOnline.model.dto.LoginResponseDTO;
import com.fesc.tiendaOnline.model.entity.UsuarioEntity;

@Service
public class AuthService {

    private final UsuarioValidationService usuarioValidationService;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UsuarioValidationService usuarioValidationService,
            JwtService jwtService) {
        this.usuarioValidationService = usuarioValidationService;
        this.jwtService = jwtService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public ResponseEntity<LoginResponseDTO> login(LoginRequestDTO loginRequest) {
        UsuarioEntity usuario = usuarioValidationService.obtenerUsuarioPorEmailConRol(loginRequest.getEmail());
        usuarioValidationService.validarUsuarioActivo(usuario,
                "Usuario no activo. Debes verificar tu cuenta primero");

        if (!passwordEncoder.matches(loginRequest.getContrasena(), usuario.getContrasenaEncp())) {
            throw new UnauthorizedException("Credenciales invalidas");
        }

        String token = jwtService.generateToken(usuario);

        LoginResponseDTO response = new LoginResponseDTO();
        response.setIdUsuario(usuario.getIdUsuario());
        response.setNombre(usuario.getNombre());
        response.setEmail(usuario.getEmail());
        response.setRol(usuario.getUsuarioRol().getRolUsuario());
        response.setUrlImagen(usuario.getUrlImagen());
        response.setExpiraEn(jwtService.getExpirationTimeToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Access-Control-Expose-Headers", HttpHeaders.AUTHORIZATION)
                .body(response);
    }
}
