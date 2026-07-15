package com.fesc.tiendaOnline.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UsuarioUpdateDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(min = 3, max = 100, message = "El apellido debe tener entre 3 y 100 caracteres")
    private String apellido;

    @NotBlank(message = "El teléfono es obligatorio")
    @Size(min = 10, max = 20, message = "El teléfono debe tener entre 10 y 20 caracteres")
    @Pattern(regexp = "^\\d+$", message = "El teléfono solo debe contener dígitos")
    private String telefono;

    @NotBlank(message = "El país es obligatorio")
    @Size(min = 3, max = 30, message = "El país debe tener entre 3 y 30 caracteres")
    private String pais;

    @NotBlank(message = "El departamento es obligatorio")
    @Size(min = 3, max = 50, message = "El departamento debe tener entre 3 y 50 caracteres")
    private String departamento;

    @NotBlank(message = "La ciudad es obligatoria")
    @Size(min = 3, max = 50, message = "La ciudad debe tener entre 3 y 50 caracteres")
    private String ciudad;

    @NotBlank(message = "La dirección es obligatoria")
    @Size(min = 10, max = 100, message = "La dirección debe tener entre 10 y 100 caracteres")
    private String direccion;

    @Size(max = 17, message = "El código postal no debe exceder 17 caracteres")
    private String codigoPostal;
}
