package com.fesc.tiendaOnline.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.fesc.tiendaOnline.model.entity.UsuarioRolEntity;
import com.fesc.tiendaOnline.repository.UsuarioRolRepository;

@Component
public class UsuarioRolDataLoader implements CommandLineRunner {
    
    private final UsuarioRolRepository usuarioRolRepository;
    
    public UsuarioRolDataLoader(UsuarioRolRepository usuarioRolRepository) {
        this.usuarioRolRepository = usuarioRolRepository;
    }
    
    @Override
    public void run(String... args) throws Exception {
        // Crear rol ADMIN si no existe
        if (usuarioRolRepository.findByRolUsuario("ADMIN").isEmpty()) {
            UsuarioRolEntity adminRol = new UsuarioRolEntity();
            adminRol.setRolUsuario("ADMIN");
            usuarioRolRepository.save(adminRol);
        }
        
        // Crear rol CLIENTE si no existe
        if (usuarioRolRepository.findByRolUsuario("CLIENTE").isEmpty()) {
            UsuarioRolEntity clienteRol = new UsuarioRolEntity();
            clienteRol.setRolUsuario("CLIENTE");
            usuarioRolRepository.save(clienteRol);
        }
    }
}
