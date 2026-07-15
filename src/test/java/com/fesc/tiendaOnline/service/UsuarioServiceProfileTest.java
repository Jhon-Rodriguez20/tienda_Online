package com.fesc.tiendaOnline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fesc.tiendaOnline.exception.ConflictException;
import com.fesc.tiendaOnline.exception.ForbiddenException;
import com.fesc.tiendaOnline.exception.NotFoundException;
import com.fesc.tiendaOnline.model.dto.UsuarioPerfilResponseDTO;
import com.fesc.tiendaOnline.model.dto.UsuarioUpdateDTO;
import com.fesc.tiendaOnline.model.entity.UsuarioEntity;
import com.fesc.tiendaOnline.model.entity.UsuarioEstado;
import com.fesc.tiendaOnline.model.entity.UsuarioRolEntity;
import com.fesc.tiendaOnline.repository.UsuarioCodigoVerificacionRepository;
import com.fesc.tiendaOnline.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceProfileTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private UsuarioCodigoVerificacionRepository usuarioCodigoVerificacionRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private UsuarioBloqueadoService bloqueoService;

    @Mock
    private UsuarioValidationService usuarioValidationService;

    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(
                usuarioRepository,
                usuarioCodigoVerificacionRepository,
                emailService,
                bloqueoService,
                usuarioValidationService, null);
        ReflectionTestUtils.setField(usuarioService, "imagenPorDefecto", "default.png");
    }

    // --- obtenerPerfil tests ---

    @Test
    @DisplayName("obtenerPerfil - inactive user throws ForbiddenException")
    void obtenerPerfil_inactiveUser_throwsForbiddenException() {
        UUID userId = UUID.randomUUID();
        UsuarioEntity usuario = buildUsuario(userId, UsuarioEstado.INACTIVO);

        when(usuarioValidationService.obtenerUsuarioPorId(userId)).thenReturn(usuario);

        assertThatThrownBy(() -> usuarioService.obtenerPerfil(userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("La cuenta no se encuentra activa");
    }

    @Test
    @DisplayName("obtenerPerfil - cancelled user throws ForbiddenException")
    void obtenerPerfil_cancelledUser_throwsForbiddenException() {
        UUID userId = UUID.randomUUID();
        UsuarioEntity usuario = buildUsuario(userId, UsuarioEstado.CANCELADO);

        when(usuarioValidationService.obtenerUsuarioPorId(userId)).thenReturn(usuario);

        assertThatThrownBy(() -> usuarioService.obtenerPerfil(userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("La cuenta no se encuentra activa");
    }

    @Test
    @DisplayName("obtenerPerfil - non-existent user throws NotFoundException")
    void obtenerPerfil_nonExistentUser_throwsNotFoundException() {
        UUID userId = UUID.randomUUID();

        when(usuarioValidationService.obtenerUsuarioPorId(userId))
                .thenThrow(new NotFoundException("Usuario no encontrado"));

        assertThatThrownBy(() -> usuarioService.obtenerPerfil(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Usuario no encontrado");
    }

    @Test
    @DisplayName("obtenerPerfil - active user returns profile correctly")
    void obtenerPerfil_activeUser_returnsProfile() {
        UUID userId = UUID.randomUUID();
        UsuarioEntity usuario = buildUsuario(userId, UsuarioEstado.ACTIVO);

        when(usuarioValidationService.obtenerUsuarioPorId(userId)).thenReturn(usuario);

        UsuarioPerfilResponseDTO result = usuarioService.obtenerPerfil(userId);

        assertThat(result.getNombre()).isEqualTo(usuario.getNombre());
        assertThat(result.getApellido()).isEqualTo(usuario.getApellido());
        assertThat(result.getEmail()).isEqualTo(usuario.getEmail());
        assertThat(result.getTelefono()).isEqualTo(usuario.getTelefono());
        assertThat(result.getPais()).isEqualTo(usuario.getPais());
        assertThat(result.getDepartamento()).isEqualTo(usuario.getDepartamento());
        assertThat(result.getCiudad()).isEqualTo(usuario.getCiudad());
        assertThat(result.getDireccion()).isEqualTo(usuario.getDireccion());
        assertThat(result.getCodigoPostal()).isEqualTo(usuario.getCodigoPostal());
    }

    // --- actualizarPerfil tests ---

    @Test
    @DisplayName("actualizarPerfil - duplicate phone throws ConflictException")
    void actualizarPerfil_duplicatePhone_throwsConflictException() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        UsuarioEntity usuario = buildUsuario(userId, UsuarioEstado.ACTIVO);
        UsuarioEntity otherUser = buildUsuario(otherUserId, UsuarioEstado.ACTIVO);
        otherUser.setTelefono("3001234567");

        UsuarioUpdateDTO dto = buildUpdateDTO();
        dto.setTelefono("3001234567");

        when(usuarioValidationService.obtenerUsuarioPorId(userId)).thenReturn(usuario);
        when(usuarioRepository.findByTelefono("3001234567")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> usuarioService.actualizarPerfil(userId, dto))
                .isInstanceOf(ConflictException.class)
                .hasMessage("El teléfono ya está registrado por otro usuario");
    }

    @Test
    @DisplayName("actualizarPerfil - success updates fields correctly")
    void actualizarPerfil_success_updatesFieldsCorrectly() {
        UUID userId = UUID.randomUUID();
        UsuarioEntity usuario = buildUsuario(userId, UsuarioEstado.ACTIVO);

        UsuarioUpdateDTO dto = buildUpdateDTO();

        when(usuarioValidationService.obtenerUsuarioPorId(userId)).thenReturn(usuario);
        when(usuarioRepository.findByTelefono(dto.getTelefono())).thenReturn(Optional.empty());
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        UsuarioPerfilResponseDTO result = usuarioService.actualizarPerfil(userId, dto);

        assertThat(result.getNombre()).isEqualTo(dto.getNombre());
        assertThat(result.getApellido()).isEqualTo(dto.getApellido());
        assertThat(result.getTelefono()).isEqualTo(dto.getTelefono());
        assertThat(result.getPais()).isEqualTo(dto.getPais());
        assertThat(result.getDepartamento()).isEqualTo(dto.getDepartamento());
        assertThat(result.getCiudad()).isEqualTo(dto.getCiudad());
        assertThat(result.getDireccion()).isEqualTo(dto.getDireccion());
        assertThat(result.getCodigoPostal()).isEqualTo(dto.getCodigoPostal());
    }

    @Test
    @DisplayName("actualizarPerfil - same user phone does not throw conflict")
    void actualizarPerfil_sameUserPhone_doesNotThrowConflict() {
        UUID userId = UUID.randomUUID();
        UsuarioEntity usuario = buildUsuario(userId, UsuarioEstado.ACTIVO);
        usuario.setTelefono("3009876543");

        UsuarioUpdateDTO dto = buildUpdateDTO();
        dto.setTelefono("3009876543");

        when(usuarioValidationService.obtenerUsuarioPorId(userId)).thenReturn(usuario);
        when(usuarioRepository.findByTelefono("3009876543")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        UsuarioPerfilResponseDTO result = usuarioService.actualizarPerfil(userId, dto);

        assertThat(result).isNotNull();
        assertThat(result.getTelefono()).isEqualTo("3009876543");
    }

    // --- Helper methods ---

    private UsuarioEntity buildUsuario(UUID id, UsuarioEstado estado) {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setIdUsuario(id);
        usuario.setNombre("Juan");
        usuario.setApellido("Pérez");
        usuario.setEmail("juan@test.com");
        usuario.setTelefono("3001112233");
        usuario.setPais("Colombia");
        usuario.setDepartamento("Cundinamarca");
        usuario.setCiudad("Bogotá");
        usuario.setDireccion("Calle 123 #45-67");
        usuario.setCodigoPostal("110111");
        usuario.setEstado(estado);
        usuario.setContrasenaEncp("$2a$10$hashedpassword");
        usuario.setUrlImagen("default.png");
        usuario.setIntentosEnvioCodigoVerificacion(0);

        UsuarioRolEntity rol = new UsuarioRolEntity();
        rol.setIdUsuarioRol(UUID.randomUUID());
        rol.setRolUsuario("CLIENTE");
        usuario.setUsuarioRol(rol);

        return usuario;
    }

    private UsuarioUpdateDTO buildUpdateDTO() {
        UsuarioUpdateDTO dto = new UsuarioUpdateDTO();
        dto.setNombre("Carlos");
        dto.setApellido("García");
        dto.setTelefono("3109876543");
        dto.setPais("Colombia");
        dto.setDepartamento("Antioquia");
        dto.setCiudad("Medellín");
        dto.setDireccion("Carrera 80 #32-10");
        dto.setCodigoPostal("050001");
        return dto;
    }
}
