package com.fesc.tiendaOnline.model.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class LoginResponseDTO {

    private UUID idUsuario;
    private String nombre;
    private String email;
    private String rol;
    private String urlImagen;
    private Long expiraEn;
    private String refreshToken;
}
