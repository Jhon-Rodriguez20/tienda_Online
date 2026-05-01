package com.fesc.tiendaOnline.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CambiarContrasenaDTO {

    @NotBlank(message = "El email es obligatorio")
    private String email;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String nuevaContrasena;

    @NotBlank(message = "Las contraseñas no coinciden")
    private String confirmarContrasena;
}
