package com.fesc.tiendaOnline.model.dto;

import lombok.Data;

@Data
public class UsuarioPerfilResponseDTO {

    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    private String pais;
    private String departamento;
    private String ciudad;
    private String direccion;
    private String codigoPostal;
}
