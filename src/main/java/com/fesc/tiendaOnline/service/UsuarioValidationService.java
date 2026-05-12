package com.fesc.tiendaOnline.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fesc.tiendaOnline.exception.ConflictException;
import com.fesc.tiendaOnline.exception.NotFoundException;
import com.fesc.tiendaOnline.model.dto.CambiarContrasenaDTO;
import com.fesc.tiendaOnline.model.dto.UsuarioCreateDTO;
import com.fesc.tiendaOnline.model.entity.UsuarioCodigoVerificacionEntity;
import com.fesc.tiendaOnline.model.entity.UsuarioEntity;
import com.fesc.tiendaOnline.model.entity.UsuarioEstado;
import com.fesc.tiendaOnline.model.entity.UsuarioRolEntity;
import com.fesc.tiendaOnline.repository.UsuarioCodigoVerificacionRepository;
import com.fesc.tiendaOnline.repository.UsuarioRepository;
import com.fesc.tiendaOnline.repository.UsuarioRolRepository;

@Service
public class UsuarioValidationService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final UsuarioCodigoVerificacionRepository usuarioCodigoVerificacionRepository;

    public UsuarioValidationService(UsuarioRepository usuarioRepository,
            UsuarioRolRepository usuarioRolRepository,
            UsuarioCodigoVerificacionRepository usuarioCodigoVerificacionRepository) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.usuarioCodigoVerificacionRepository = usuarioCodigoVerificacionRepository;
    }

    public UsuarioEntity obtenerUsuarioPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    public UsuarioEntity obtenerUsuarioPorIdRolAdmin(UUID idAdminRol) {
        return usuarioRepository.findByIdWithRol(idAdminRol)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    public UsuarioEntity obtenerUsuarioPorEmailConRol(String email) {
        return usuarioRepository.findByEmailWithRol(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    public UsuarioEntity obtenerUsuarioPorId(UUID idUsuario) {
        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    public UsuarioRolEntity validarYObtenerRol(UsuarioCreateDTO usuarioCreateDTO) {
        validarEmailDisponible(usuarioCreateDTO.getEmail());

        String rolNombre = usuarioCreateDTO.getRol() != null
                ? usuarioCreateDTO.getRol().toUpperCase()
                : "CLIENTE";

        validarCreacionAdministrador(rolNombre, usuarioCreateDTO.getEmail());

        return usuarioRolRepository.findByRolUsuario(rolNombre)
                .orElseThrow(() -> new BusinessRuleException("Rol no valido"));
    }

    public void validarUsuarioActivo(UsuarioEntity usuario, String mensaje) {
        if (usuario.getEstado() != UsuarioEstado.ACTIVO) {
            throw new BusinessRuleException(mensaje);
        }
    }

    public void validarUsuarioNoActivo(UsuarioEntity usuario, String mensaje) {
        if (usuario.getEstado() == UsuarioEstado.ACTIVO) {
            throw new BusinessRuleException(mensaje);
        }
    }

    public void validarUsuarioNoCancelado(UsuarioEntity usuario, String mensaje) {
        if (usuario.getEstado() == UsuarioEstado.CANCELADO) {
            throw new BusinessRuleException(mensaje);
        }
    }

    public void validarUsuarioNoBloqueado(UsuarioEntity usuario) {
        if (usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(LocalDateTime.now())) {
            long minutosRestantes = Duration.between(LocalDateTime.now(), usuario.getBloqueadoHasta()).toMinutes();
            throw new BusinessRuleException(
                    "Usuario bloqueado. Intenta nuevamente en " + minutosRestantes + " minutos");
        }
    }

    public boolean reiniciarBloqueoSiExpirado(UsuarioEntity usuario) {
        if (usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isBefore(LocalDateTime.now())) {
            usuario.setBloqueadoHasta(null);
            usuario.setIntentosEnvioCodigoVerificacion(0);
            return true;
        }

        return false;
    }

    public UsuarioCodigoVerificacionEntity obtenerCodigoPorUsuario(UsuarioEntity usuario, String mensajeNoEncontrado) {
        return usuarioCodigoVerificacionRepository.findByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException(mensajeNoEncontrado));
    }

    public void validarCodigoNoExpirado(UsuarioCodigoVerificacionEntity codigoEntity,
            UUID idUsuario,
            String mensajeExpirado) {
        if (codigoEntity.getExpiracion().isBefore(LocalDateTime.now())) {
            usuarioCodigoVerificacionRepository.deleteByIdUsuario(idUsuario);
            throw new BusinessRuleException(mensajeExpirado);
        }
    }

    public void validarCodigoCoincide(String codigoActual, String codigoRecibido, String mensaje) {
        if (!codigoActual.equals(codigoRecibido)) {
            throw new BusinessRuleException(mensaje);
        }
    }

    public void validarContrasenasCoinciden(CambiarContrasenaDTO cambiarDTO) {
        if (!cambiarDTO.getNuevaContrasena().equals(cambiarDTO.getConfirmarContrasena())) {
            throw new BusinessRuleException("Las contrasenas no coinciden");
        }
    }

    public void validarTelefonoDisponible(String telefono) {
        if (usuarioRepository.findByTelefono(telefono).isPresent()) {
            throw new ConflictException("El teléfono ya está registrado");
        }
    }
    
    private void validarEmailDisponible(String email) {
        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("El correo electronico ya está registrado");
        }
    }

    private void validarCreacionAdministrador(String rolNombre, String email) {
        if (!"ADMIN".equals(rolNombre)) {
            return;
        }

        boolean yaExisteAdmin = usuarioRepository.findAll().stream()
                .anyMatch(usuario -> usuario.getUsuarioRol().getRolUsuario().equals("ADMIN"));

        if (yaExisteAdmin) {
            throw new ConflictException("Ya existe un usuario administrador. No se pueden crear mas");
        }
    }
}
