package com.fesc.tiendaOnline.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CancelarCuentaDTO {

    @NotBlank(message = "La contraseña es obligatoria para cancelar la cuenta")
    private String contrasena;
}
