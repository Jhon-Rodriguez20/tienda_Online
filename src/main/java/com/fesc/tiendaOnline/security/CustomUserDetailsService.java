package com.fesc.tiendaOnline.security;

import java.util.UUID;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.fesc.tiendaOnline.model.entity.UsuarioEntity;
import com.fesc.tiendaOnline.repository.UsuarioRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    
    public CustomUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UsuarioEntity usuario = findUsuario(username);

        return new UserDetailsImpl(usuario);
    }

    private UsuarioEntity findUsuario(String username) {
        try {
            UUID idUsuario = UUID.fromString(username);
            return usuarioRepository.findByIdWithRol(idUsuario)
                    .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con id: " + username));
        } catch (IllegalArgumentException e) {
            return usuarioRepository.findByEmailWithRol(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + username));
        }
    }
}
