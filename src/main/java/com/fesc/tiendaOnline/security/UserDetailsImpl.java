package com.fesc.tiendaOnline.security;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fesc.tiendaOnline.model.entity.UsuarioEntity;

public class UserDetailsImpl implements UserDetails {
    
    private final UsuarioEntity usuario;
    
    public UserDetailsImpl(UsuarioEntity usuario) {
        this.usuario = usuario;
    }
    
    public UsuarioEntity getUsuario() {
        return usuario;
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(
            new SimpleGrantedAuthority("ROLE_" + usuario.getUsuarioRol().getRolUsuario())
        );
    }
    
    @Override
    public String getPassword() {
        return usuario.getContrasenaEncp();
    }
    
    @Override
    public String getUsername() {
        return usuario.getEmail();
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        LocalDateTime bloqueadoHasta = usuario.getBloqueadoHasta();
        return bloqueadoHasta == null || bloqueadoHasta.isBefore(LocalDateTime.now());
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return usuario.getEstado().toString().equals("ACTIVO");
    }
}
