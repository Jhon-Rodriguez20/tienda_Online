package com.fesc.tiendaOnline.mapper;

import org.springframework.stereotype.Component;

import com.fesc.tiendaOnline.model.dto.UsuarioPerfilResponseDTO;
import com.fesc.tiendaOnline.model.dto.UsuarioResponseDTO;
import com.fesc.tiendaOnline.model.entity.UsuarioEntity;

@Component
public class UsuarioMapper {

    public UsuarioResponseDTO toResponse(UsuarioEntity usuario) {
        UsuarioResponseDTO response = new UsuarioResponseDTO();
        response.setIdUsuario(usuario.getIdUsuario());
        response.setNombre(usuario.getNombre());
        response.setEmail(usuario.getEmail());
        response.setTelefono(usuario.getTelefono());
        response.setPais(usuario.getPais());
        response.setDireccion(usuario.getDireccion());
        response.setDepartamento(usuario.getDepartamento());
        response.setCiudad(usuario.getCiudad());
        response.setCodigoPostal(usuario.getCodigoPostal());
        response.setEstado(usuario.getEstado().toString());
        response.setRol(usuario.getUsuarioRol().getRolUsuario());
        response.setUrlImagen(usuario.getUrlImagen());
        return response;
    }

    public UsuarioPerfilResponseDTO toPerfilResponse(UsuarioEntity usuario) {
        UsuarioPerfilResponseDTO dto = new UsuarioPerfilResponseDTO();
        dto.setNombre(usuario.getNombre());
        dto.setApellido(usuario.getApellido());
        dto.setEmail(usuario.getEmail());
        dto.setTelefono(usuario.getTelefono());
        dto.setPais(usuario.getPais());
        dto.setDepartamento(usuario.getDepartamento());
        dto.setCiudad(usuario.getCiudad());
        dto.setDireccion(usuario.getDireccion());
        dto.setCodigoPostal(usuario.getCodigoPostal());
        return dto;
    }
}
