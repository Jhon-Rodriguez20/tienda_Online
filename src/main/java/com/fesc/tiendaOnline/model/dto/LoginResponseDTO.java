package com.fesc.tiendaOnline.model.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
public class LoginResponseDTO {

    private UUID idUsuario;
    private String nombre;
    private String email;
    private String telefono;
    private String pais;
    private String ciudad;
    private String departamento;
    private String direccion;
    private String codigoPostal;
    private String rol;
    private String urlImagen;
    private Long expiraEn;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String refreshToken;
}
