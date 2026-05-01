package com.fesc.tiendaOnline.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.fesc.tiendaOnline.model.entity.UsuarioEntity;
import com.fesc.tiendaOnline.model.entity.UsuarioEstado;
import com.fesc.tiendaOnline.model.entity.UsuarioRolEntity;
import com.fesc.tiendaOnline.repository.UsuarioRepository;
import com.fesc.tiendaOnline.repository.UsuarioRolRepository;
import com.fesc.tiendaOnline.service.BusinessRuleException;

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
            admin.setContrasenaEncp(passwordEncoder.encode("Canchos02026"));
            admin.setEstado(UsuarioEstado.ACTIVO);
            admin.setUsuarioRol(rolAdmin);
            admin.setIntentosEnvioCodigoVerificacion(0);
            admin.setBloqueadoHasta(null);
            admin.setUrlImagen("sin imagen perfil");
            
            usuarioRepository.save(admin);
        }
    }
}