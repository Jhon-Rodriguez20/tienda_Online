package com.fesc.tiendaOnline.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UsuarioVerificacionDTO {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email debe ser válido")
    private String email;

    @NotBlank(message = "El código de verificación es obligatorio")
    @Pattern(regexp = "\\d{6}", message = "El código de verificación debe tener 6 dígitos")
    private String codigoVerificacion;

}
