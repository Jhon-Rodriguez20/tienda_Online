package com.fesc.tiendaOnline.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fesc.tiendaOnline.model.entity.UsuarioEntity;
import com.fesc.tiendaOnline.repository.UsuarioRepository;

@Service
public class AdminValidationService {

    private final UsuarioRepository usuarioRepository;

    public AdminValidationService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

        public UsuarioEntity validarAdmin(UUID idUsuario) {
        UsuarioEntity usuario = usuarioRepository.findByIdWithRol(idUsuario)
            .orElseThrow(() -> new BusinessRuleException("Usuario no encontrado"));
                
        if (!usuario.getUsuarioRol().getRolUsuario().equals("ADMIN")) {
            throw new BusinessRuleException("No tienes permisos para realizar esta acción");
        }
        return usuario;
    }
    
    public UsuarioEntity validarAdminPorEmail(String email) {
        UsuarioEntity usuario = usuarioRepository.findByEmailWithRol(email)
            .orElseThrow(() -> new BusinessRuleException("Usuario no encontrado"));
        
        if (!usuario.getUsuarioRol().getRolUsuario().equals("ADMIN")) {
            throw new BusinessRuleException("No tienes permisos para realizar esta acción");
        }
        return usuario;
    }
}
