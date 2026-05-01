package com.fesc.tiendaOnline.model.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class UsuarioResponseDTO {

    private UUID idUsuario;
    private String nombre;
    private String email;
    private String estado;
    private String rol;
    private String urlImagen;
}
