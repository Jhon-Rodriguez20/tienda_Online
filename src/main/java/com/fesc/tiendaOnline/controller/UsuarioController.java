package com.fesc.tiendaOnline.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fesc.tiendaOnline.model.dto.CambiarContrasenaDTO;
import com.fesc.tiendaOnline.model.dto.CancelarCuentaDTO;
import com.fesc.tiendaOnline.model.dto.SolicitudRecuperacionContrasenaDTO;
import com.fesc.tiendaOnline.model.dto.UsuarioCreateDTO;
import com.fesc.tiendaOnline.model.dto.UsuarioPerfilResponseDTO;
import com.fesc.tiendaOnline.model.dto.UsuarioReenvioCodigoDTO;
import com.fesc.tiendaOnline.model.dto.UsuarioResponseDTO;
import com.fesc.tiendaOnline.model.dto.UsuarioUpdateDTO;
import com.fesc.tiendaOnline.model.dto.UsuarioVerificacionDTO;
import com.fesc.tiendaOnline.model.dto.VerificarCodigoRecuperacionContrasenaDTO;
import com.fesc.tiendaOnline.security.UserDetailsImpl;
import com.fesc.tiendaOnline.service.UsuarioService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/usuario")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/registro")
    public ResponseEntity<UsuarioResponseDTO> registrarUsuario(@Valid @RequestBody UsuarioCreateDTO usuarioCreateDTO) {
        UsuarioResponseDTO usuarioCreado = usuarioService.crearUsuario(usuarioCreateDTO);
        return new ResponseEntity<>(usuarioCreado, HttpStatus.CREATED);
    }

    @PostMapping("/verificar")
    public ResponseEntity<Map<String, String>> verificarCodigo(@Valid @RequestBody UsuarioVerificacionDTO usuarioVerificacionDTO) {
        usuarioService.verificarCodigo(usuarioVerificacionDTO);
        Map<String, String> response = new HashMap<>();
        response.put("mensaje", "Código verificado correctamente.");
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reenviar-codigo")
    public ResponseEntity<Map<String, String>> reenviarCodigo(@Valid @RequestBody UsuarioReenvioCodigoDTO usuarioReenvioCodigoDTO) {
        usuarioService.reenviarCodigoVerificacion(usuarioReenvioCodigoDTO);
        Map<String, String> response = new HashMap<>();
        response.put("mensaje", "Código de verificación reenviado exitosamente.");
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/recuperar/solicitar")
    public ResponseEntity<Map<String, String>> solicitarRecuperacion(@Valid @RequestBody SolicitudRecuperacionContrasenaDTO solicitudRecuperacionContrasenaDTO) {
        usuarioService.solicitarRecuperacionContrasena(solicitudRecuperacionContrasenaDTO);
        Map<String, String> response = new HashMap<>();
        response.put("mensaje", "Código de recuperación enviado exitosamente.");
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/recuperar/verificar")
    public ResponseEntity<Map<String, String>> verificarCodigoRecuperacion(@Valid @RequestBody VerificarCodigoRecuperacionContrasenaDTO verificarCodigoRecuperacionContrasenaDTO) {
        usuarioService.verificarCodigoRecuperacion(verificarCodigoRecuperacionContrasenaDTO);
        Map<String, String> response = new HashMap<>();
        response.put("mensaje", "Código de recuperación verificado correctamente.");
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/recuperar/cambiar-contrasena")
    public ResponseEntity<Map<String, String>> cambiarContrasena(@Valid @RequestBody CambiarContrasenaDTO cambiarContrasenaDTO) {
        usuarioService.cambiarContrasenaRecuperacion(cambiarContrasenaDTO);
        Map<String, String> response = new HashMap<>();
        response.put("mensaje", "Contraseña cambiada correctamente.");
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/cancelar-cuenta")
    public ResponseEntity<Map<String, String>> cancelarCuenta(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody CancelarCuentaDTO cancelarDTO) {

        UUID idUsuario = principal.getUsuario().getIdUsuario();
        usuarioService.cancelarCuenta(idUsuario, cancelarDTO);

        Map<String, String> response = new HashMap<>();
        response.put("mensaje", "Tu cuenta ha sido cancelada exitosamente.");
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/perfil")
    public ResponseEntity<UsuarioPerfilResponseDTO> obtenerPerfil(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID idUsuario = principal.getUsuario().getIdUsuario();
        UsuarioPerfilResponseDTO perfil = usuarioService.obtenerPerfil(idUsuario);
        return ResponseEntity.ok(perfil);
    }

    @PutMapping("/perfil")
    public ResponseEntity<UsuarioPerfilResponseDTO> actualizarPerfil(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody UsuarioUpdateDTO usuarioUpdateDTO) {
                
        UUID idUsuario = principal.getUsuario().getIdUsuario();
        UsuarioPerfilResponseDTO perfilActualizado = usuarioService.actualizarPerfil(idUsuario, usuarioUpdateDTO);
        return ResponseEntity.ok(perfilActualizado);
    }
}
