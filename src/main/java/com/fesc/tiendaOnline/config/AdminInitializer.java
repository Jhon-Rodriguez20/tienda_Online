package com.fesc.tiendaOnline.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.fesc.tiendaOnline.exception.BusinessRuleException;
import com.fesc.tiendaOnline.model.entity.UsuarioEntity;
import com.fesc.tiendaOnline.model.entity.UsuarioEstado;
import com.fesc.tiendaOnline.model.entity.UsuarioRolEntity;
import com.fesc.tiendaOnline.repository.UsuarioRepository;
import com.fesc.tiendaOnline.repository.UsuarioRolRepository;

@Component
public class AdminInitializer implements CommandLineRunner {
    
    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    
    public AdminInitializer(UsuarioRepository usuarioRepository,
                           UsuarioRolRepository usuarioRolRepository) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }
    
    @Override
    public void run(String... args) throws Exception {
        // Buscar rol ADMIN por nombre
        UsuarioRolEntity rolAdmin = usuarioRolRepository.findByRolUsuario("ADMIN")
                .orElseThrow(() -> new BusinessRuleException("Rol ADMIN no encontrado"));
        
        // Verificar si ya existe un ADMIN por email
        boolean existeAdmin = usuarioRepository.findByEmail("developjarz@gmail.com").isPresent();
        
        if (!existeAdmin) {
            UsuarioEntity admin = new UsuarioEntity();
            admin.setNombre("Administrador");
            admin.setEmail("developjarz@gmail.com");
            admin.setTelefono("1234567890");
            admin.setPais("Colombia");
            admin.setDireccion("Direccion del administrador");
            admin.setDepartamento("Departamento del administrador");
            admin.setCiudad("Ciudad del administrador");
            admin.setCodigoPostal("Sin código postal");
            admin.setContrasenaEncp(passwordEncoder.encode("C46sjfe084kvuw94hnldo0hd3948"));
            admin.setEstado(UsuarioEstado.ACTIVO);
            admin.setUsuarioRol(rolAdmin);
            admin.setIntentosEnvioCodigoVerificacion(0);
            admin.setBloqueadoHasta(null);
            admin.setUrlImagen("/images/sinImagenPerfil.webp");
            
            usuarioRepository.save(admin);
        }
    }
}