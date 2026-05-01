package com.fesc.tiendaOnline.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActualizarEstadoCompraDTO {

    @NotNull(message = "El estado es obligatorio")
    private String estado;
}
