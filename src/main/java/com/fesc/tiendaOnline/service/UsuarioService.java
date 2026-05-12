package com.fesc.tiendaOnline.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fesc.tiendaOnline.exception.UnauthorizedException;
import com.fesc.tiendaOnline.model.dto.CambiarContrasenaDTO;
import com.fesc.tiendaOnline.model.dto.CancelarCuentaDTO;
import com.fesc.tiendaOnline.model.dto.SolicitudRecuperacionContrasenaDTO;
import com.fesc.tiendaOnline.model.dto.UsuarioCreateDTO;
import com.fesc.tiendaOnline.model.dto.UsuarioReenvioCodigoDTO;
import com.fesc.tiendaOnline.model.dto.UsuarioResponseDTO;
import com.fesc.tiendaOnline.model.dto.UsuarioVerificacionDTO;
import com.fesc.tiendaOnline.model.dto.VerificarCodigoRecuperacionContrasenaDTO;
import com.fesc.tiendaOnline.model.entity.UsuarioCodigoVerificacionEntity;
import com.fesc.tiendaOnline.model.entity.UsuarioEntity;
import com.fesc.tiendaOnline.model.entity.UsuarioEstado;
import com.fesc.tiendaOnline.model.entity.UsuarioRolEntity;
import com.fesc.tiendaOnline.repository.UsuarioCodigoVerificacionRepository;
import com.fesc.tiendaOnline.repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioCodigoVerificacionRepository usuarioCodigoVerificacionRepository;
    private final EmailService emailService;
    private final UsuarioBloqueadoService bloqueoService;
    private final UsuarioValidationService usuarioValidationService;

    @Value("${imagen.perfil.por.defecto}")
    private String imagenPorDefecto;

    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final int MAX_INTENTOS = 3;
    private static final int TIEMPO_BLOQUEO_MINUTOS = 30;
    private static final int CODIGO_EXPIRACION_MINUTOS = 15;

    public UsuarioService(UsuarioRepository usuarioRepository,
            UsuarioCodigoVerificacionRepository usuarioCodigoVerificacionRepository,
            EmailService emailService,
            UsuarioBloqueadoService bloqueoService,
            UsuarioValidationService usuarioValidationService) {
        
        this.usuarioRepository = usuarioRepository;
        this.usuarioCodigoVerificacionRepository = usuarioCodigoVerificacionRepository;
        this.emailService = emailService;
        this.bloqueoService = bloqueoService;
        this.usuarioValidationService = usuarioValidationService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Transactional
    public UsuarioResponseDTO crearUsuario(UsuarioCreateDTO usuarioCreateDTO) {
        UsuarioRolEntity rol = usuarioValidationService.validarYObtenerRol(usuarioCreateDTO);
        usuarioValidationService.validarTelefonoDisponible(usuarioCreateDTO.getTelefono());

        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNombre(usuarioCreateDTO.getNombre());
        usuario.setEmail(usuarioCreateDTO.getEmail());
        usuario.setTelefono(usuarioCreateDTO.getTelefono());
        usuario.setPais(usuarioCreateDTO.getPais());
        usuario.setDireccion(usuarioCreateDTO.getDireccion());
        usuario.setCiudad(usuarioCreateDTO.getCiudad());
        usuario.setCodigoPostal(usuarioCreateDTO.getCodigoPostal());
        usuario.setDepartamento(usuarioCreateDTO.getDepartamento());
        usuario.setContrasenaEncp(passwordEncoder.encode(usuarioCreateDTO.getContrasena()));
        usuario.setEstado(UsuarioEstado.INACTIVO);
        usuario.setUsuarioRol(rol);
        usuario.setIntentosEnvioCodigoVerificacion(0);
        usuario.setBloqueadoHasta(null);
        usuario.setUrlImagen(imagenPorDefecto);

        UsuarioEntity usuarioGuardado = usuarioRepository.save(usuario);
        generarYEnviarCodigoVerificacion(usuarioGuardado);

        return convertirAResponseDTO(usuarioGuardado);
    }

    @Transactional
    public void verificarCodigo(UsuarioVerificacionDTO verificacionDTO) {
        UsuarioEntity usuario = usuarioValidationService.obtenerUsuarioPorEmailConRol(verificacionDTO.getEmail());

        usuarioValidationService.validarUsuarioNoActivo(usuario, "El usuario ya esta activo");
        usuarioValidationService.validarUsuarioNoBloqueado(usuario);
        reiniciarBloqueoSiEsNecesario(usuario);

        UsuarioCodigoVerificacionEntity codigoEntity = usuarioValidationService.obtenerCodigoPorUsuario(
                usuario, "Codigo de verificacion no encontrado. Solicita un nuevo codigo");

        usuarioValidationService.validarCodigoNoExpirado(codigoEntity, usuario.getIdUsuario(),
                "El codigo ha expirado. Solicita un nuevo codigo");
        usuarioValidationService.validarCodigoCoincide(codigoEntity.getCodigoVerificacion(),
                verificacionDTO.getCodigoVerificacion(), "Codigo de verificacion incorrecto");

        usuario.setEstado(UsuarioEstado.ACTIVO);
        usuario.setIntentosEnvioCodigoVerificacion(0);
        usuario.setBloqueadoHasta(null);
        usuarioRepository.save(usuario);
        usuarioCodigoVerificacionRepository.deleteByIdUsuario(usuario.getIdUsuario());
    }

    @Transactional
    public void reenviarCodigoVerificacion(UsuarioReenvioCodigoDTO reenvioDTO) {
        UsuarioEntity usuario = usuarioValidationService.obtenerUsuarioPorEmail(reenvioDTO.getEmail());

        usuarioValidationService.validarUsuarioNoActivo(usuario,
                "El usuario ya esta activo, no es necesario reenviar codigo");
        usuarioValidationService.validarUsuarioNoBloqueado(usuario);
        reiniciarBloqueoSiEsNecesario(usuario);

        if (usuario.getIntentosEnvioCodigoVerificacion() >= MAX_INTENTOS) {
            bloqueoService.guardarBloqueo(usuario, TIEMPO_BLOQUEO_MINUTOS);
            throw new BusinessRuleException(
                    "Has excedido el numero maximo de intentos. Usuario bloqueado por 30 minutos");
        }
        usuario.setIntentosEnvioCodigoVerificacion(usuario.getIntentosEnvioCodigoVerificacion() + 1);
        usuarioRepository.save(usuario);

        generarYEnviarCodigoVerificacion(usuario);
    }

    @Transactional
    public void solicitarRecuperacionContrasena(
            SolicitudRecuperacionContrasenaDTO solicitudRecuperacionContrasenaDTO) {
        UsuarioEntity usuario = usuarioValidationService
                .obtenerUsuarioPorEmail(solicitudRecuperacionContrasenaDTO.getEmail());

        usuarioValidationService.validarUsuarioActivo(usuario,
                "No puedes recuperar la contrasena. El usuario no esta activo");
        usuarioValidationService.validarUsuarioNoBloqueado(usuario);

        generarYEnviarCodigoVerificacion(usuario);
    }

    @Transactional
    public void verificarCodigoRecuperacion(VerificarCodigoRecuperacionContrasenaDTO verificarDTO) {
        UsuarioEntity usuario = usuarioValidationService.obtenerUsuarioPorEmail(verificarDTO.getEmail());

        usuarioValidationService.validarUsuarioActivo(usuario, "El usuario no esta activo");
        usuarioValidationService.validarUsuarioNoBloqueado(usuario);

        UsuarioCodigoVerificacionEntity codigoEntity = usuarioValidationService.obtenerCodigoPorUsuario(usuario,
                "No hay codigo de recuperacion activo. Solicita uno nuevo");
        usuarioValidationService.validarCodigoNoExpirado(codigoEntity, usuario.getIdUsuario(),
                "El codigo ha expirado. Solicita un nuevo codigo");

        if (!codigoEntity.getCodigoVerificacion().equals(verificarDTO.getCodigoVerificacion())) {
            manejarIntentoFallidoRecuperacion(usuario);
            throw new BusinessRuleException("Codigo de verificacion incorrecto");
        }
    }

    @Transactional
    public void cambiarContrasenaRecuperacion(CambiarContrasenaDTO cambiarDTO) {
        usuarioValidationService.validarContrasenasCoinciden(cambiarDTO);

        UsuarioEntity usuario = usuarioValidationService.obtenerUsuarioPorEmail(cambiarDTO.getEmail());
        usuarioValidationService.validarUsuarioActivo(usuario, "El usuario no esta activo");

        UsuarioCodigoVerificacionEntity codigoEntity = usuarioValidationService.obtenerCodigoPorUsuario(usuario,
                "No hay proceso de recuperacion activo. Solicita un nuevo codigo");
        usuarioValidationService.validarCodigoNoExpirado(codigoEntity, usuario.getIdUsuario(),
                "El proceso de recuperacion ha expirado. Solicita un nuevo codigo");

        usuario.setContrasenaEncp(passwordEncoder.encode(cambiarDTO.getNuevaContrasena()));
        usuarioRepository.save(usuario);
        usuarioCodigoVerificacionRepository.deleteByIdUsuario(usuario.getIdUsuario());
        emailService.enviarConfirmacionCambioContrasena(usuario.getEmail());
    }

    public void cancelarCuenta(UUID idUsuario, CancelarCuentaDTO cancelarCuentaDTO) {
        UsuarioEntity usuario = usuarioValidationService.obtenerUsuarioPorId(idUsuario);
        usuarioValidationService.validarUsuarioNoCancelado(usuario, "La cuenta ya esta cancelada");

        if (!passwordEncoder.matches(cancelarCuentaDTO.getContrasena(), usuario.getContrasenaEncp())) {
            throw new UnauthorizedException("Contrasena incorrecta");
        }
        usuario.setEstado(UsuarioEstado.CANCELADO);
        usuarioRepository.save(usuario);

        usuarioCodigoVerificacionRepository.deleteByIdUsuario(usuario.getIdUsuario());
        emailService.enviarConfirmacionCancelacionCuenta(usuario.getEmail());
    }

    private void manejarIntentoFallidoRecuperacion(UsuarioEntity usuario) {
        int intentos = usuario.getIntentosEnvioCodigoVerificacion() + 1;
        usuario.setIntentosEnvioCodigoVerificacion(intentos);

        if (intentos >= MAX_INTENTOS) {
            bloqueoService.guardarBloqueo(usuario, TIEMPO_BLOQUEO_MINUTOS);
            throw new BusinessRuleException(
                    "Has excedido el numero maximo de intentos. Usuario bloqueado por 30 minutos");
        }
        usuarioRepository.save(usuario);
    }

    private void reiniciarBloqueoSiEsNecesario(UsuarioEntity usuario) {
        if (usuarioValidationService.reiniciarBloqueoSiExpirado(usuario)) {
            usuarioRepository.save(usuario);
        }
    }

    private void generarYEnviarCodigoVerificacion(UsuarioEntity usuario) {
        usuarioCodigoVerificacionRepository.deleteByIdUsuario(usuario.getIdUsuario());
        usuarioCodigoVerificacionRepository.flush();

        String codigo = String.format("%06d", secureRandom.nextInt(1000000));

        UsuarioCodigoVerificacionEntity codigoEntity = new UsuarioCodigoVerificacionEntity();
        codigoEntity.setUsuario(usuario);
        codigoEntity.setCodigoVerificacion(codigo);
        codigoEntity.setExpiracion(LocalDateTime.now().plusMinutes(CODIGO_EXPIRACION_MINUTOS));

        usuarioCodigoVerificacionRepository.save(codigoEntity);
        emailService.enviarCodigoVerificacion(usuario.getEmail(), codigo);
    }

    private UsuarioResponseDTO convertirAResponseDTO(UsuarioEntity usuarioEntity) {
        UsuarioResponseDTO responseDTO = new UsuarioResponseDTO();
        responseDTO.setIdUsuario(usuarioEntity.getIdUsuario());
        responseDTO.setNombre(usuarioEntity.getNombre());
        responseDTO.setEmail(usuarioEntity.getEmail());
        responseDTO.setTelefono(usuarioEntity.getTelefono());
        responseDTO.setPais(usuarioEntity.getPais());
        responseDTO.setDireccion(usuarioEntity.getDireccion());
        responseDTO.setDepartamento(usuarioEntity.getDepartamento());
        responseDTO.setCiudad(usuarioEntity.getCiudad());
        responseDTO.setCodigoPostal(usuarioEntity.getCodigoPostal() != null ? usuarioEntity.getCodigoPostal() : "Sin código postal");
        responseDTO.setEstado(usuarioEntity.getEstado().toString());
        responseDTO.setRol(usuarioEntity.getUsuarioRol().getRolUsuario());
        responseDTO.setUrlImagen(usuarioEntity.getUrlImagen());
        
        return responseDTO;
    }
}
