package com.fesc.tiendaOnline.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CambiarContrasenaDTO {

    @NotBlank(message = "El email es obligatorio")
    private String email;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @jakarta.validation.constraints.Pattern(
        regexp = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,}$",
        message = "La contraseña debe contener al menos una letra, un número y un carácter especial"
    )
    private String nuevaContrasena;

    @NotBlank(message = "Las contraseñas no coinciden")
    private String confirmarContrasena;
}
