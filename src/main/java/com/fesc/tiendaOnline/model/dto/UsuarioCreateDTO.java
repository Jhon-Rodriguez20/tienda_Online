package com.fesc.tiendaOnline.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UsuarioCreateDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    private String nombre;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email debe ser válido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, max = 100, message = "La contraseña debe tener al menos 6 caracteres")    
    private String contrasena;

    @NotBlank(message = "El teléfono es obligatorio")
    @Size(min = 10, max = 20, message = "La contraseña debe tener entre 10 y 20 caracteres")    
    private String telefono;

    @NotBlank(message = "El país es obligatorio")
    @Size(min = 3, max = 30, message = "El país debe tener entre 3 y 30 caracteres")    
    private String pais;

    @NotBlank(message = "La ciudad es obligatoria")
    @Size(min = 3, max = 50, message = "La ciudad debe tener entre 3 y 50 caracteres")    
    private String ciudad;
    
    @NotBlank(message = "La dirección es obligatoria")
    @Size(min = 10, max = 100, message = "La dirección debe tener entre 10 y 100 caracteres")    
    private String direccion;

    @NotBlank(message = "El departamento/provincia es obligatorio")
    @Size(min = 3, max = 50, message = "El departamento/provincia debe tener entre 3 y 50 caracteres")
    private String departamento;
    
    private String codigoPostal;

    private String rol;
}
